/**
 * @platform/adapter-k6 — K6 handleSummary adapter
 *
 * Publishes K6 load-test results (checks + performance metrics) to the
 * Test Automation Platform after each run.
 *
 * Usage — add to your K6 script:
 *
 *   import { platformHandleSummary } from './platform-k6-adapter.js';
 *   export { platformHandleSummary as handleSummary };
 *
 * Or wrap your own handleSummary:
 *
 *   import { publishToPlatform } from './platform-k6-adapter.js';
 *   export function handleSummary(data) {
 *     publishToPlatform(data);
 *     return { stdout: '...' };
 *   }
 *
 * Required env vars:
 *   PLATFORM_URL         — base URL, e.g. https://platform.internal
 *   PLATFORM_API_KEY     — API key (X-API-Key header)
 *   PLATFORM_PROJECT_ID  — project slug (auto-created on first run)
 *   PLATFORM_TEAM_ID     — team slug (auto-created on first run)
 *
 * Optional env vars:
 *   PLATFORM_BRANCH      — git branch (auto-detected from CI env vars)
 *   PLATFORM_ENVIRONMENT — environment label, e.g. staging, production
 *   PLATFORM_COMMIT_SHA  — commit SHA (auto-detected from CI env vars)
 *   PLATFORM_SUITE_NAME  — label for this load-test scenario
 *   PLATFORM_CI_RUN_URL  — link to CI job (auto-detected for GitHub Actions)
 *
 * Compatible with K6 >= 0.38.0 (http available in handleSummary).
 */

import http from 'k6/http';

/**
 * Drop-in handleSummary replacement.
 * Returns K6 text summary to stdout + publishes to the platform.
 */
export function platformHandleSummary(data) {
    publishToPlatform(data);
    return buildLocalOutputs(data);
}

/**
 * Fire-and-forget publish — use this when you want to keep your own handleSummary
 * and just add platform publishing.
 */
export function publishToPlatform(data) {
    const url    = __ENV.PLATFORM_URL;
    const apiKey = __ENV.PLATFORM_API_KEY;

    if (!url || !apiKey) {
        console.warn('[Platform] PLATFORM_URL or PLATFORM_API_KEY not set — skipping publish');
        return;
    }

    const projectId  = __ENV.PLATFORM_PROJECT_ID || 'k6-project';
    const teamId     = __ENV.PLATFORM_TEAM_ID    || 'k6-team';
    const branch     = __ENV.PLATFORM_BRANCH     || detectBranch();
    const environment = __ENV.PLATFORM_ENVIRONMENT || 'unknown';
    const commitSha  = __ENV.PLATFORM_COMMIT_SHA  || detectCommitSha();
    const suiteName  = __ENV.PLATFORM_SUITE_NAME  || 'k6-load-test';
    const ciRunUrl   = __ENV.PLATFORM_CI_RUN_URL  || detectCiRunUrl();

    const summaryJson = JSON.stringify(data);

    const formData = {
        files:       http.file(summaryJson, 'summary.json', 'application/json'),
        format:      'K6',
        projectId,
        teamId,
        branch,
        environment,
        suiteName,
    };
    if (commitSha) formData.commitSha = commitSha;
    if (ciRunUrl)  formData.ciRunUrl  = ciRunUrl;

    try {
        const res = http.post(
            `${url}/api/v1/results/ingest`,
            formData,
            {
                headers: { 'X-API-Key': apiKey },
                timeout: '30s',
            }
        );
        if (res && res.status >= 200 && res.status < 300) {
            console.log(`[Platform] Published K6 results — runId: ${parseRunId(res.body)}`);
        } else {
            const statusCode = res ? res.status : 'no response';
            const body = res ? res.body : '';
            console.warn(`[Platform] Publish failed: HTTP ${statusCode} — ${body}`);
        }
    } catch (e) {
        console.warn(`[Platform] Error publishing results: ${e}`);
    }
}

// ── CI environment auto-detection ────────────────────────────────────────────

function detectBranch() {
    return (
        __ENV.GITHUB_REF_NAME        ||  // GitHub Actions
        __ENV.CI_COMMIT_BRANCH       ||  // GitLab CI
        __ENV.GIT_BRANCH             ||  // Jenkins
        __ENV.CIRCLE_BRANCH          ||  // CircleCI
        __ENV.BUILD_SOURCEBRANCH     ||  // Azure DevOps (refs/heads/main)
        __ENV.BITBUCKET_BRANCH       ||  // Bitbucket
        __ENV.TRAVIS_BRANCH          ||  // Travis CI
        __ENV.BUILDKITE_BRANCH       ||  // Buildkite
        'unknown'
    );
}

function detectCommitSha() {
    return (
        __ENV.GITHUB_SHA             ||
        __ENV.CI_COMMIT_SHA          ||
        __ENV.GIT_COMMIT             ||
        __ENV.CIRCLE_SHA1            ||
        __ENV.BUILD_SOURCEVERSION    ||
        __ENV.BITBUCKET_COMMIT       ||
        __ENV.TRAVIS_COMMIT          ||
        __ENV.BUILDKITE_COMMIT       ||
        null
    );
}

function detectCiRunUrl() {
    // GitHub Actions
    if (__ENV.GITHUB_SERVER_URL && __ENV.GITHUB_REPOSITORY && __ENV.GITHUB_RUN_ID) {
        return `${__ENV.GITHUB_SERVER_URL}/${__ENV.GITHUB_REPOSITORY}/actions/runs/${__ENV.GITHUB_RUN_ID}`;
    }
    // GitLab CI
    if (__ENV.CI_JOB_URL) return __ENV.CI_JOB_URL;
    // CircleCI
    if (__ENV.CIRCLE_BUILD_URL) return __ENV.CIRCLE_BUILD_URL;
    // Jenkins
    if (__ENV.BUILD_URL) return __ENV.BUILD_URL;
    // Azure DevOps
    if (__ENV.SYSTEM_TEAMFOUNDATIONCOLLECTIONURI && __ENV.BUILD_BUILDID) {
        return `${__ENV.SYSTEM_TEAMFOUNDATIONCOLLECTIONURI}/_build/results?buildId=${__ENV.BUILD_BUILDID}`;
    }
    return null;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseRunId(body) {
    try {
        const obj = JSON.parse(body);
        return obj.runId || '(unknown)';
    } catch (_) {
        return '(unknown)';
    }
}

/**
 * Minimal text summary fallback — prints pass/fail check counts and key metrics.
 * Teams that want the full K6 default output should import textSummary from
 * https://jslib.k6.io/k6-summary/0.0.2/index.js and call it themselves.
 */
function buildLocalOutputs(data) {
    const checks   = data.root_group && data.root_group.checks ? data.root_group.checks : [];
    const passed   = checks.filter(c => c.fails === 0 && c.passes > 0).length;
    const failed   = checks.filter(c => c.fails > 0).length;
    const metrics  = data.metrics || {};
    const p95      = metrics.http_req_duration ? metrics.http_req_duration['p(95)'] : null;
    const reqRate  = metrics.http_reqs ? metrics.http_reqs.rate : null;
    const errRate  = metrics.http_req_failed
        ? (metrics.http_req_failed.fails / (metrics.http_req_failed.passes + metrics.http_req_failed.fails) * 100)
        : null;

    let summary = `\n[Platform K6 Adapter]\n`;
    summary    += `  Checks   : ${passed} passed, ${failed} failed\n`;
    if (p95 !== null && p95 !== undefined) summary    += `  p(95)    : ${p95.toFixed(1)} ms\n`;
    if (reqRate !== null && reqRate !== undefined) summary    += `  Req/s    : ${reqRate.toFixed(1)}\n`;
    if (errRate !== null && errRate !== undefined) summary    += `  Error %  : ${errRate.toFixed(2)}%\n`;

    return { stdout: summary };
}
