package com.platform.common.integration;

/** All supported external system types, grouped by the tier they feed. */
public enum IntegrationType {

    // Tier 1 — Requirements
    JIRA_CLOUD,
    JIRA_SERVER,
    LINEAR,
    GITHUB_ISSUES,
    AZURE_DEVOPS_BOARDS,
    SHORTCUT,

    // Tier 2 — Test Cases
    JIRA_XRAY,
    JIRA_ZEPHYR,
    TESTRAIL,
    QASE,
    TESTMO,
    PLATFORM_NATIVE,   // managed directly inside this platform, no external system

    // Tier 3 — Automated Tests (source repos)
    GITHUB,
    GITLAB,
    BITBUCKET,
    AZURE_DEVOPS_REPOS,

    // Tier 4 — Executions (CI systems)
    GITHUB_ACTIONS,
    JENKINS,
    GITLAB_CI,
    AZURE_DEVOPS_PIPELINES,
    CIRCLECI,
    TEAMCITY,
    BUILDKITE,

    // Tier 5 — Monitors
    DATADOG,
    PAGERDUTY,
    GRAFANA,
    NEW_RELIC,
    CLOUDWATCH,
    OPSGENIE
}
