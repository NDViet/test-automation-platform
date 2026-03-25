package com.platform.ingestion.parser;

import com.platform.common.dto.PerformanceMetricsDto;
import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.common.enums.TestType;
import com.platform.ingestion.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;

/**
 * Parses JMeter JTL XML results file ({@code testResults}).
 *
 * <p>Each unique {@code lb} (label) attribute on {@code <httpSample>} / {@code <sample>} elements
 * becomes one test case. Samples with {@code s="false"} are counted as failures.
 * The test case is FAILED if any sample failed; PASSED otherwise.</p>
 *
 * <p>Generate with JMeter: {@code jmeter -n -t test.jmx -l results.jtl}</p>
 */
@Component
public class JMeterJtlParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(JMeterJtlParser.class);

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.JMETER;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No JMeter JTL files provided", SourceFormat.JMETER);
        }

        // label → aggregate stats
        Map<String, LabelStats> statsMap = new LinkedHashMap<>();

        for (byte[] file : files) {
            try {
                Document doc = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new ByteArrayInputStream(file));
                doc.getDocumentElement().normalize();

                // Parse <httpSample> and <sample> elements
                parseSamples(doc.getElementsByTagName("httpSample"), statsMap);
                parseSamples(doc.getElementsByTagName("sample"), statsMap);

            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse JMeter JTL: " + e.getMessage(), SourceFormat.JMETER, e);
            }
        }

        if (statsMap.isEmpty()) {
            throw new ParseException("No samples found in JMeter JTL file", SourceFormat.JMETER);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();
        for (Map.Entry<String, LabelStats> entry : statsMap.entrySet()) {
            try {
                allCases.add(buildTestCase(entry.getKey(), entry.getValue()));
            } catch (Exception e) {
                log.warn("Skipping JMeter label '{}': {}", entry.getKey(), e.getMessage());
            }
        }

        int passed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.PASSED).count();
        int failed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.FAILED).count();
        int skipped = (int) allCases.stream().filter(t -> t.status() == TestStatus.SKIPPED).count();
        int broken  = (int) allCases.stream().filter(t -> t.status() == TestStatus.BROKEN).count();
        long totalMs = allCases.stream()
                .mapToLong(t -> t.durationMs() != null ? t.durationMs() : 0L).sum();

        // Compute aggregate performance metrics from the label stats map.
        // JTL doesn't include p90/p95 natively without extra configuration — those are left null.
        long totalSamples   = statsMap.values().stream().mapToLong(s -> s.total).sum();
        long totalElapsed   = statsMap.values().stream().mapToLong(s -> s.totalElapsed).sum();
        long totalFailures  = statsMap.values().stream().mapToLong(s -> s.failures).sum();
        Double avgMs        = totalSamples > 0 ? (double) totalElapsed / totalSamples : null;
        Double errorRate    = totalSamples > 0 ? (double) totalFailures / totalSamples : null;
        PerformanceMetricsDto perfMetrics = PerformanceMetricsDto.of(
                avgMs, null, null, null, null, null, null,
                totalSamples, null,
                errorRate, null, totalMs > 0 ? totalMs : null);

        return List.of(new UnifiedTestResult(
                ctx.runId(), ctx.teamId(), ctx.projectId(),
                ctx.branch(), ctx.environment(), ctx.commitSha(),
                null, ctx.ciProvider(), ctx.ciRunUrl(), Instant.now(),
                allCases.size(), passed, failed, skipped, broken,
                totalMs > 0 ? totalMs : null,
                SourceFormat.JMETER, allCases,
                ctx.executionMode(), ctx.parallelism(), ctx.suiteName(),
                TestType.PERFORMANCE, perfMetrics
        ));
    }

    private void parseSamples(NodeList nodes, Map<String, LabelStats> statsMap) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String label   = el.getAttribute("lb");
            if (label.isBlank()) continue;

            long elapsed   = parseLong(el.getAttribute("t"), 0);
            boolean success = !"false".equalsIgnoreCase(el.getAttribute("s"));
            String rc      = el.getAttribute("rc");
            String rm      = el.getAttribute("rm");

            statsMap.computeIfAbsent(label, k -> new LabelStats())
                    .add(elapsed, success, rc, rm);
        }
    }

    private TestCaseResultDto buildTestCase(String label, LabelStats stats) {
        TestStatus status;
        String failureMessage = null;

        if (stats.failures == 0) {
            status = TestStatus.PASSED;
        } else {
            status = TestStatus.FAILED;
            double failRate = (double) stats.failures / stats.total * 100;
            failureMessage = String.format(
                    "%.1f%% error rate (%d/%d samples failed); last error: %s %s",
                    failRate, stats.failures, stats.total,
                    stats.lastErrorRc, stats.lastErrorRm);
        }

        return TestCaseResultDto.basic(
                label, label, label, label,
                List.of(), status, stats.meanElapsed(),
                failureMessage, null, 0, List.of()
        );
    }

    private static long parseLong(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static class LabelStats {
        int total;
        int failures;
        long totalElapsed;
        String lastErrorRc = "";
        String lastErrorRm = "";

        void add(long elapsed, boolean success, String rc, String rm) {
            total++;
            totalElapsed += elapsed;
            if (!success) {
                failures++;
                lastErrorRc = rc;
                lastErrorRm = rm;
            }
        }

        long meanElapsed() {
            return total > 0 ? totalElapsed / total : 0;
        }
    }
}
