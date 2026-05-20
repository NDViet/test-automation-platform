package com.platform.common.model;

/** The five quality tiers that form the platform's canonical traceability chain. */
public enum Tier {
    REQUIREMENT,    // What needs to be built — JIRA, Linear, GitHub Issues
    TEST_CASE,      // How we verify it — Xray, TestRail, Zephyr, agent-generated
    AUTOMATED_TEST, // Code that verifies it — test methods from source repos
    EXECUTION,      // A CI run of automated tests — GitHub Actions, Jenkins, etc.
    MONITOR         // Production health — Datadog, PagerDuty, Grafana alerts
}
