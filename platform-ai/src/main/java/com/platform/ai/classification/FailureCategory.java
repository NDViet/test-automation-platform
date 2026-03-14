package com.platform.ai.classification;

/**
 * Root-cause category assigned by Claude to each test failure.
 */
public enum FailureCategory {

    /** A genuine defect in the application code under test. */
    APPLICATION_BUG,

    /** A bug or fragility inside the test itself (bad assertion, wrong selector, etc.). */
    TEST_DEFECT,

    /** Infrastructure, configuration, or environmental issue (CI agent, missing env var, etc.). */
    ENVIRONMENT,

    /** Race condition, timing dependency, or async wait causing intermittent failures. */
    FLAKY_TIMING,

    /** External service, database, or test-data dependency is unavailable or misbehaving. */
    DEPENDENCY,

    /** Insufficient information to classify with confidence. */
    UNKNOWN
}
