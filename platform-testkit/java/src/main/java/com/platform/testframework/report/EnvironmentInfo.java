package com.platform.testframework.report;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures runtime environment metadata for debugging context.
 *
 * <p>Attached to every test result so the platform knows exactly which environment
 * a failure came from — critical for environment-specific vs application bugs.
 * Also detects execution mode (parallel/sequential) and CI provider.</p>
 */
public final class EnvironmentInfo {

    private EnvironmentInfo() {}

    /**
     * Returns a map of standard environment properties.
     * Teams can augment this with their own keys (APP_URL, BROWSER, DEVICE, etc.)
     */
    public static Map<String, String> collect() {
        Map<String, String> env = new LinkedHashMap<>();

        // JVM
        env.put("java.version",    System.getProperty("java.version", "unknown"));
        env.put("java.vendor",     System.getProperty("java.vendor", "unknown"));
        env.put("jvm.name",        System.getProperty("java.vm.name", "unknown"));

        // OS
        env.put("os.name",         System.getProperty("os.name", "unknown"));
        env.put("os.arch",         System.getProperty("os.arch", "unknown"));
        env.put("os.version",      System.getProperty("os.version", "unknown"));

        // CI detection
        String ciProvider = detectCiProvider();
        env.put("ci.provider", ciProvider);

        String buildId  = detectBuildId(ciProvider);
        String buildUrl = detectBuildUrl(ciProvider);
        if (buildId  != null) env.put("ci.build.id",  buildId);
        if (buildUrl != null) env.put("ci.build.url", buildUrl);

        // Commit/branch metadata (multi-CI)
        String commit = resolveEnv("GITHUB_SHA", "CI_COMMIT_SHA", "GIT_COMMIT", null);
        String branch = resolveEnv("GITHUB_REF_NAME", "CI_COMMIT_REF_NAME", "GIT_BRANCH", null);
        if (commit != null) env.put("ci.commit", commit);
        if (branch != null) env.put("ci.branch", branch);

        // Execution mode
        env.put("execution.mode",        detectExecutionMode());
        env.put("execution.parallelism", String.valueOf(detectParallelism()));

        // Test env override
        String testEnv = System.getenv("TEST_ENV");
        if (testEnv != null && !testEnv.isBlank())
            env.put("test.env", testEnv);
        String appUrl = System.getenv("APP_URL");
        if (appUrl != null && !appUrl.isBlank())
            env.put("app.url", appUrl);

        return env;
    }

    /** Returns one of: github-actions, gitlab-ci, jenkins, circleci, azure-devops,
     *  bitbucket, teamcity, travis-ci, ci (generic), local */
    public static String detectCiProvider() {
        if (System.getenv("GITHUB_ACTIONS")        != null) return "github-actions";
        if (System.getenv("GITLAB_CI")             != null) return "gitlab-ci";
        if (System.getenv("JENKINS_URL")           != null) return "jenkins";
        if (System.getenv("CIRCLECI")              != null) return "circleci";
        if (System.getenv("TF_BUILD")              != null) return "azure-devops";
        if (System.getenv("BITBUCKET_BUILD_NUMBER")!= null) return "bitbucket";
        if (System.getenv("TEAMCITY_VERSION")      != null) return "teamcity";
        if (System.getenv("TRAVIS")                != null) return "travis-ci";
        if (System.getenv("CI")                    != null) return "ci";
        return "local";
    }

    /** Returns PARALLEL, SEQUENTIAL, or UNKNOWN. */
    public static String detectExecutionMode() {
        // JUnit 5 — explicit flag
        String junitParallel = System.getProperty("junit.jupiter.execution.parallel.enabled");
        if ("true".equalsIgnoreCase(junitParallel)) return "PARALLEL";

        // Maven Surefire fork-based parallelism
        int forkCount = parseForkCount(System.getProperty("surefire.forkCount",
                System.getProperty("forkCount", "0")));
        if (forkCount > 1) return "PARALLEL";

        // Cucumber / any framework: infer from the resolved thread count.
        // Works because pom.xml sets junit.jupiter.execution.parallel.config.fixed.parallelism
        // to the same value used for cucumber.execution.parallel.config.fixed.parallelism.
        int parallelism = detectParallelism();
        if (parallelism > 1) return "PARALLEL";
        if (parallelism == 1) return "SEQUENTIAL";

        // Explicit override via env var (useful in CI pipelines)
        String override = System.getenv("TEST_EXECUTION_MODE");
        if (override != null && !override.isBlank()) return override.toUpperCase();

        // JUnit parallel explicitly disabled
        if ("false".equalsIgnoreCase(junitParallel)) return "SEQUENTIAL";

        return "UNKNOWN";
    }

    /** Returns number of parallel threads/forks; 0 = unknown. */
    public static int detectParallelism() {
        // JUnit 5 fixed parallelism
        String fixedParallelism = System.getProperty("junit.jupiter.execution.parallel.config.fixed.parallelism");
        if (fixedParallelism != null) {
            try { return Integer.parseInt(fixedParallelism.trim()); } catch (NumberFormatException ignored) {}
        }

        // JUnit 5 dynamic factor (factor × CPU count)
        String dynamicFactor = System.getProperty("junit.jupiter.execution.parallel.config.dynamic.factor");
        if (dynamicFactor != null) {
            try {
                double factor = Double.parseDouble(dynamicFactor.trim());
                return Math.max(1, (int) (factor * Runtime.getRuntime().availableProcessors()));
            } catch (NumberFormatException ignored) {}
        }

        // Maven Surefire forkCount
        int forkCount = parseForkCount(System.getProperty("surefire.forkCount",
                System.getProperty("forkCount", "0")));
        if (forkCount > 0) return forkCount;

        return 0;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String detectBuildId(String ciProvider) {
        return switch (ciProvider) {
            case "github-actions" -> System.getenv("GITHUB_RUN_ID");
            case "gitlab-ci"      -> System.getenv("CI_PIPELINE_ID");
            case "jenkins"        -> System.getenv("BUILD_NUMBER");
            case "circleci"       -> System.getenv("CIRCLE_BUILD_NUM");
            case "azure-devops"   -> System.getenv("BUILD_BUILDID");
            case "bitbucket"      -> System.getenv("BITBUCKET_BUILD_NUMBER");
            case "teamcity"       -> System.getenv("BUILD_NUMBER");
            case "travis-ci"      -> System.getenv("TRAVIS_BUILD_NUMBER");
            default               -> null;
        };
    }

    private static String detectBuildUrl(String ciProvider) {
        return switch (ciProvider) {
            case "github-actions" -> {
                String server = System.getenv("GITHUB_SERVER_URL");
                String repo   = System.getenv("GITHUB_REPOSITORY");
                String runId  = System.getenv("GITHUB_RUN_ID");
                yield (server != null && repo != null && runId != null)
                        ? server + "/" + repo + "/actions/runs/" + runId : null;
            }
            case "gitlab-ci"    -> System.getenv("CI_PIPELINE_URL");
            case "jenkins"      -> System.getenv("BUILD_URL");
            case "circleci"     -> System.getenv("CIRCLE_BUILD_URL");
            case "azure-devops" -> {
                String collection = System.getenv("SYSTEM_TEAMFOUNDATIONCOLLECTIONURI");
                String project    = System.getenv("SYSTEM_TEAMPROJECT");
                String buildId    = System.getenv("BUILD_BUILDID");
                yield (collection != null && project != null && buildId != null)
                        ? collection + project + "/_build/results?buildId=" + buildId : null;
            }
            case "bitbucket" -> {
                String workspace = System.getenv("BITBUCKET_WORKSPACE");
                String repo      = System.getenv("BITBUCKET_REPO_SLUG");
                String buildNum  = System.getenv("BITBUCKET_BUILD_NUMBER");
                yield (workspace != null && repo != null && buildNum != null)
                        ? "https://bitbucket.org/" + workspace + "/" + repo + "/addon/pipelines/home#!/results/" + buildNum : null;
            }
            case "travis-ci" -> System.getenv("TRAVIS_BUILD_WEB_URL");
            default          -> null;
        };
    }

    /**
     * Parses Maven Surefire forkCount — supports plain integers and the {@code 2C} (CPU multiplier) format.
     */
    private static int parseForkCount(String value) {
        if (value == null || value.isBlank()) return 0;
        value = value.trim();
        try {
            if (value.endsWith("C") || value.endsWith("c")) {
                double factor = Double.parseDouble(value.substring(0, value.length() - 1));
                return Math.max(1, (int) (factor * Runtime.getRuntime().availableProcessors()));
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String resolveEnv(String k1, String k2, String k3, String fallback) {
        String v = System.getenv(k1);
        if (v != null && !v.isBlank()) return v;
        if (k2 != null) { v = System.getenv(k2); if (v != null && !v.isBlank()) return v; }
        if (k3 != null) { v = System.getenv(k3); if (v != null && !v.isBlank()) return v; }
        return fallback;
    }
}
