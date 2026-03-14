package com.platform.common.enums;

public enum SourceFormat {
    JUNIT_XML,
    CUCUMBER_JSON,
    TESTNG,
    ALLURE,
    PLAYWRIGHT,
    NEWMAN,
    /** Direct JSON payload from platform-testframework — no file parsing needed. */
    PLATFORM_NATIVE,
    /** k6 --summary-export JSON (performance tests). */
    K6,
    /** Gatling stats.json (performance tests). */
    GATLING,
    /** JMeter JTL XML (performance tests). */
    JMETER
}
