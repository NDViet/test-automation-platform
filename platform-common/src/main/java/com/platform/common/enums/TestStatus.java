package com.platform.common.enums;

public enum TestStatus {
    PASSED,
    FAILED,
    SKIPPED,
    BROKEN   // test error (exception outside assertion), distinct from assertion failure
}
