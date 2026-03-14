package com.platform.testframework.context;

import java.util.UUID;

/**
 * Holds stable, JVM-scoped context for the duration of one {@code mvn test} /
 * {@code gradle test} invocation.
 *
 * <p>All test cases in the same execution share {@code run_id}, {@code hostname},
 * {@code ci_provider}, and {@code build_id}. These are set as MDC fields by
 * {@link com.platform.testframework.extension.PlatformExtension} and
 * {@link com.platform.testframework.cucumber.PlatformCucumberPlugin} before each
 * test so every log line produced during a run is tagged and queryable in OpenSearch:</p>
 *
 * <pre>
 * GET /test_execution_logs-YYYY.MM.dd/_search
 * { "query": { "bool": { "must": [
 *     { "term": { "run_id":      "3f4a1b2c-..." } },
 *     { "term": { "ci_provider": "github-actions" } }
 * ]}}}
 * </pre>
 *
 * <p>Override the generated run ID by setting the {@code PLATFORM_RUN_ID} environment
 * variable or the {@code platform.runId} system property.</p>
 */
public final class RunContext {

    /** One UUID per JVM start — resolved once, never changes during the run. */
    private static final String RUN_ID      = resolveRunId();
    /** Hostname of the machine running the tests. */
    private static final String HOSTNAME    = resolveHostname();
    /** CI provider name, or "local" when running on a developer machine. */
    private static final String CI_PROVIDER = resolveCiProvider();
    /** CI build / run number; empty string when running locally. */
    private static final String BUILD_ID    = resolveBuildId();

    private RunContext() {}

    public static String getRunId()      { return RUN_ID; }
    public static String getHostname()   { return HOSTNAME; }
    public static String getCiProvider() { return CI_PROVIDER; }
    public static String getBuildId()    { return BUILD_ID; }

    // ── resolvers ─────────────────────────────────────────────────────────────

    private static String resolveRunId() {
        String prop = System.getProperty("platform.runId");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("PLATFORM_RUN_ID");
        if (env != null && !env.isBlank()) return env;
        return UUID.randomUUID().toString();
    }

    private static String resolveHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String h = System.getenv("HOSTNAME");
            return (h != null && !h.isBlank()) ? h : "unknown";
        }
    }

    private static String resolveCiProvider() {
        if (System.getenv("GITHUB_ACTIONS")         != null) return "github-actions";
        if (System.getenv("GITLAB_CI")              != null) return "gitlab-ci";
        if (System.getenv("JENKINS_URL")            != null) return "jenkins";
        if (System.getenv("CIRCLECI")               != null) return "circleci";
        if (System.getenv("TF_BUILD")               != null) return "azure-devops";
        if (System.getenv("BITBUCKET_BUILD_NUMBER") != null) return "bitbucket";
        if (System.getenv("TEAMCITY_VERSION")       != null) return "teamcity";
        if (System.getenv("TRAVIS")                 != null) return "travis-ci";
        if (System.getenv("CI")                     != null) return "ci";
        return "local";
    }

    /** Resolved after CI_PROVIDER is initialised (static field order guarantee). */
    private static String resolveBuildId() {
        String v = switch (CI_PROVIDER) {
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
        return v != null ? v : "";
    }
}
