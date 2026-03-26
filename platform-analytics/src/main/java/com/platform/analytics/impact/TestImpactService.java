package com.platform.analytics.impact;

import com.platform.core.domain.TiaEvent;
import com.platform.core.repository.TiaEventRepository;
import com.platform.core.repository.TestCoverageMappingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyses a set of changed source files and returns the minimal subset of tests
 * that cover those changes — enabling selective ("smart") test execution in CI.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Convert each changed file path to a fully qualified class name
 *       (e.g. {@code src/main/java/com/example/Foo.java} → {@code com.example.Foo}).</li>
 *   <li>Query coverage mappings for matching class names.</li>
 *   <li>Collect distinct test IDs that cover any matched class.</li>
 *   <li>Assess risk: are all changed classes covered? What % is uncovered?</li>
 * </ol>
 *
 * <p>Every call is recorded as a {@link TiaEvent} row for dashboard trending.</p>
 */
@Service
public class TestImpactService {

    private static final Logger log = LoggerFactory.getLogger(TestImpactService.class);

    private final TestCoverageMappingRepository coverageRepo;
    private final TiaEventRepository            tiaEventRepo;

    private final Counter queriesTotal;
    private final Timer   queryTimer;

    public TestImpactService(TestCoverageMappingRepository coverageRepo,
                             TiaEventRepository tiaEventRepo,
                             MeterRegistry meterRegistry) {
        this.coverageRepo = coverageRepo;
        this.tiaEventRepo = tiaEventRepo;
        this.queriesTotal = Counter.builder("tia.queries.total")
                .description("Total number of TIA analyse() calls")
                .register(meterRegistry);
        this.queryTimer = Timer.builder("tia.query.duration")
                .description("Duration of TIA analyse() calls")
                .register(meterRegistry);
    }

    @Transactional
    public TestImpactResult analyse(UUID projectId, List<String> changedFiles,
                                    List<String> changedClasses, int totalTestCount) {
        return analyse(projectId, changedFiles, changedClasses, totalTestCount, null, "api");
    }

    @Transactional
    public TestImpactResult analyse(UUID projectId, List<String> changedFiles,
                                    List<String> changedClasses, int totalTestCount,
                                    String branch, String triggeredBy) {

        return queryTimer.record(() -> doAnalyse(
                projectId, changedFiles, changedClasses, totalTestCount, branch, triggeredBy));
    }

    private TestImpactResult doAnalyse(UUID projectId, List<String> changedFiles,
                                       List<String> changedClasses, int totalTestCount,
                                       String branch, String triggeredBy) {
        // Normalise inputs to fully qualified class names
        Set<String> classesToCheck = new LinkedHashSet<>();
        for (String file : (changedFiles != null ? changedFiles : List.<String>of())) {
            String cls = filePathToClassName(file);
            if (cls != null) classesToCheck.add(cls);
        }
        if (changedClasses != null) {
            changedClasses.stream()
                    .filter(c -> c != null && !c.isBlank())
                    .forEach(classesToCheck::add);
        }

        if (classesToCheck.isEmpty()) {
            return TestImpactResult.noChanges(totalTestCount);
        }
        queriesTotal.increment();

        // Query coverage mappings — exact class names
        var mappings = coverageRepo.findByProjectIdAndClassNameIn(projectId, classesToCheck);

        // Group: class → set of test IDs
        Map<String, Set<String>> classToTests = new LinkedHashMap<>();
        for (var m : mappings) {
            classToTests
                    .computeIfAbsent(m.getClassName(), k -> new LinkedHashSet<>())
                    .add(m.getTestCaseId());
        }

        // Resolve wildcard mappings — e.g. @AffectedBy("com.example.catalog.*")
        // matches any changed class whose package starts with "com.example.catalog."
        var wildcardMappings = coverageRepo.findWildcardMappingsByProject(projectId);
        for (var wm : wildcardMappings) {
            String pattern = wm.getClassName(); // "com.example.catalog.*"
            String prefix  = pattern.substring(0, pattern.length() - 1); // "com.example.catalog."
            for (String cls : classesToCheck) {
                if (cls.startsWith(prefix)) {
                    classToTests
                            .computeIfAbsent(cls, k -> new LinkedHashSet<>())
                            .add(wm.getTestCaseId());
                }
            }
        }

        Set<String> recommendedTests = classToTests.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> coveredClasses = classToTests.keySet();
        Set<String> uncoveredClasses = new LinkedHashSet<>(classesToCheck);
        uncoveredClasses.removeAll(coveredClasses);

        double coveredRatio = classesToCheck.isEmpty() ? 1.0
                : (double) coveredClasses.size() / classesToCheck.size();

        RiskLevel risk = computeRisk(coveredRatio, uncoveredClasses.size());

        int selected = recommendedTests.size();
        double reduction = totalTestCount > 0
                ? (1.0 - (double) selected / totalTestCount) * 100.0 : 0.0;

        log.info("TIA projectId={} changedClasses={} recommended={}/{} risk={}",
                projectId, classesToCheck.size(), selected, totalTestCount, risk);

        TestImpactResult result = new TestImpactResult(
                new ArrayList<>(recommendedTests),
                totalTestCount,
                selected,
                String.format("%.0f%%", Math.max(0, reduction)),
                risk,
                new ArrayList<>(uncoveredClasses),
                new ArrayList<>(classesToCheck),
                buildJunit5Filter(recommendedTests),
                buildMavenFilter(recommendedTests),
                buildGradleFilter(recommendedTests)
        );

        // Persist event for dashboard trending
        tiaEventRepo.save(new TiaEvent(
                projectId,
                classesToCheck.size(),
                totalTestCount,
                selected,
                uncoveredClasses.size(),
                Math.max(0, reduction),
                risk.name(),
                branch,
                triggeredBy
        ));

        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Converts a source file path to a fully qualified class name.
     * Handles standard Java/Kotlin/Scala conventions.
     * Returns null if the file is not a recognisable source file.
     *
     * Examples:
     *   src/main/java/com/example/PaymentService.java  → com.example.PaymentService
     *   src/main/kotlin/com/example/CartService.kt      → com.example.CartService
     *   src/app/services/payment.service.ts             → payment.service  (module path)
     *   lib/checkout/checkout.js                        → lib/checkout/checkout (module path)
     */
    static String filePathToClassName(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        filePath = filePath.replace('\\', '/');

        // Java / Kotlin / Scala: strip well-known source roots
        for (String root : List.of(
                "src/main/java/", "src/test/java/",
                "src/main/kotlin/", "src/test/kotlin/",
                "src/main/scala/", "src/test/scala/")) {
            int idx = filePath.indexOf(root);
            if (idx >= 0) {
                String relative = filePath.substring(idx + root.length());
                // strip extension and convert / to .
                return stripExtension(relative).replace('/', '.');
            }
        }

        // TypeScript/JavaScript — strip common prefixes, use as module path
        for (String root : List.of("src/", "lib/", "app/", "packages/")) {
            if (filePath.startsWith(root)) {
                return stripExtension(filePath.substring(root.length())).replace('/', '.');
            }
        }

        // Fallback: use the path with slashes converted to dots, extension stripped
        return stripExtension(filePath).replace('/', '.');
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(0, dot) : path;
    }

    private RiskLevel computeRisk(double coveredRatio, int uncoveredCount) {
        if (uncoveredCount == 0)    return RiskLevel.LOW;
        if (coveredRatio >= 0.80)   return RiskLevel.MEDIUM;
        if (coveredRatio >= 0.50)   return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    /** JUnit 5 / Maven Surefire groups filter expression. */
    private String buildJunit5Filter(Set<String> testIds) {
        if (testIds.isEmpty()) return "";
        // Surefire -Dtest= accepts comma-separated Class#method patterns
        return testIds.stream()
                .map(id -> {
                    int hash = id.lastIndexOf('#');
                    return hash > 0 ? id.substring(0, hash) + "#" + id.substring(hash + 1) : id;
                })
                .collect(Collectors.joining(","));
    }

    /** Maven -Dtest= value (same as JUnit 5 filter for Surefire). */
    private String buildMavenFilter(Set<String> testIds) {
        return buildJunit5Filter(testIds);
    }

    /** Gradle test filter (--tests) — space-separated list. */
    private String buildGradleFilter(Set<String> testIds) {
        if (testIds.isEmpty()) return "";
        return testIds.stream()
                .map(id -> {
                    int hash = id.lastIndexOf('#');
                    return hash > 0 ? id.substring(0, hash) + "." + id.substring(hash + 1) : id;
                })
                .map(id -> "--tests \"" + id + "\"")
                .collect(Collectors.joining(" "));
    }
}
