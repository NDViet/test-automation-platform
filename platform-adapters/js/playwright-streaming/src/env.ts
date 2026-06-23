export function detectBranch(): string | undefined {
  return (
    process.env['GITHUB_HEAD_REF'] ||            // PR source branch (GitHub)
    process.env['GITHUB_REF_NAME'] ||            // branch or tag name (GitHub)
    process.env['CI_COMMIT_REF_NAME'] ||         // GitLab
    process.env['GIT_BRANCH'] ||                 // Jenkins
    process.env['CIRCLE_BRANCH'] ||              // CircleCI
    process.env['BUILD_SOURCEBRANCH']?.replace('refs/heads/', '') || // Azure DevOps
    process.env['BITBUCKET_BRANCH'] ||           // Bitbucket
    process.env['TRAVIS_BRANCH'] ||              // Travis
    process.env['BUILDKITE_BRANCH'] ||           // Buildkite
    undefined
  )
}

export function detectCommitSha(): string | undefined {
  return (
    process.env['GITHUB_SHA'] ||
    process.env['CI_COMMIT_SHA'] ||
    process.env['GIT_COMMIT'] ||
    process.env['CIRCLE_SHA1'] ||
    process.env['BUILD_SOURCEVERSION'] ||
    process.env['BITBUCKET_COMMIT'] ||
    process.env['TRAVIS_COMMIT'] ||
    process.env['BUILDKITE_COMMIT'] ||
    undefined
  )
}

export function detectCiRunUrl(): string | undefined {
  if (process.env['GITHUB_SERVER_URL'] && process.env['GITHUB_REPOSITORY'] && process.env['GITHUB_RUN_ID']) {
    return `${process.env['GITHUB_SERVER_URL']}/${process.env['GITHUB_REPOSITORY']}/actions/runs/${process.env['GITHUB_RUN_ID']}`
  }
  if (process.env['CI_JOB_URL']) return process.env['CI_JOB_URL']                       // GitLab
  if (process.env['CIRCLE_BUILD_URL']) return process.env['CIRCLE_BUILD_URL']            // CircleCI
  if (process.env['SYSTEM_TEAMFOUNDATIONCOLLECTIONURI'] && process.env['BUILD_BUILDID']) {
    return `${process.env['SYSTEM_TEAMFOUNDATIONCOLLECTIONURI']}${process.env['SYSTEM_TEAMPROJECT']}/_build/results?buildId=${process.env['BUILD_BUILDID']}`
  }
  if (process.env['BUILDKITE_BUILD_URL']) return process.env['BUILDKITE_BUILD_URL']      // Buildkite
  if (process.env['TRAVIS_BUILD_WEB_URL']) return process.env['TRAVIS_BUILD_WEB_URL']    // Travis
  return undefined
}

export function isCI(): boolean {
  return !!(
    process.env['CI'] ||
    process.env['GITHUB_ACTIONS'] ||
    process.env['GITLAB_CI'] ||
    process.env['JENKINS_URL'] ||
    process.env['CIRCLECI'] ||
    process.env['TF_BUILD'] ||
    process.env['BITBUCKET_BUILD_NUMBER'] ||
    process.env['TEAMCITY_VERSION'] ||
    process.env['TRAVIS'] ||
    process.env['BUILDKITE']
  )
}

/** Which CI system is running this job. Returns a normalized lowercase slug. */
export function detectCiProvider(): string | undefined {
  if (process.env['GITHUB_ACTIONS'])        return 'github'
  if (process.env['GITLAB_CI'])             return 'gitlab'
  if (process.env['CIRCLECI'])              return 'circleci'
  if (process.env['TF_BUILD'])              return 'azure-devops'
  if (process.env['JENKINS_URL'])           return 'jenkins'
  if (process.env['BITBUCKET_BUILD_NUMBER']) return 'bitbucket'
  if (process.env['TRAVIS'])               return 'travis'
  if (process.env['BUILDKITE'])            return 'buildkite'
  if (process.env['TEAMCITY_VERSION'])     return 'teamcity'
  return undefined
}

/**
 * Workflow / pipeline name.
 * GitHub: $GITHUB_WORKFLOW  ("CI", "Release", "Playwright Tests", etc.)
 * GitLab: $CI_PIPELINE_NAME
 * Azure DevOps: $BUILD_DEFINITIONNAME
 * Buildkite: $BUILDKITE_PIPELINE_NAME
 */
export function detectWorkflow(): string | undefined {
  return (
    process.env['GITHUB_WORKFLOW'] ||
    process.env['CI_PIPELINE_NAME'] ||
    process.env['BUILD_DEFINITIONNAME'] ||
    process.env['BUILDKITE_PIPELINE_NAME'] ||
    process.env['TRAVIS_JOB_NAME'] ||
    undefined
  )
}

/**
 * The event that triggered this run.
 * GitHub: $GITHUB_EVENT_NAME — "push" | "pull_request" | "schedule" | "workflow_dispatch" | …
 * GitLab: derived from $CI_PIPELINE_SOURCE
 */
export function detectTrigger(): string | undefined {
  if (process.env['GITHUB_EVENT_NAME']) return process.env['GITHUB_EVENT_NAME']
  // GitLab pipeline sources
  if (process.env['CI_PIPELINE_SOURCE']) return process.env['CI_PIPELINE_SOURCE']
  return undefined
}

/**
 * Pull request / merge request number.
 * GitHub: extracted from $GITHUB_REF  (refs/pull/123/merge)
 * GitLab: $CI_MERGE_REQUEST_IID
 * Bitbucket: $BITBUCKET_PR_ID
 * Azure DevOps: $SYSTEM_PULLREQUEST_PULLREQUESTID
 */
export function detectPrNumber(): number | undefined {
  // GitHub — GITHUB_REF = "refs/pull/123/merge" for PR builds
  const ghRef = process.env['GITHUB_REF']
  if (ghRef) {
    const m = ghRef.match(/^refs\/pull\/(\d+)\//)
    if (m) return parseInt(m[1], 10)
  }
  const gl = process.env['CI_MERGE_REQUEST_IID']
  if (gl) return parseInt(gl, 10)

  const bb = process.env['BITBUCKET_PR_ID']
  if (bb) return parseInt(bb, 10)

  const ado = process.env['SYSTEM_PULLREQUEST_PULLREQUESTID']
  if (ado) return parseInt(ado, 10)

  return undefined
}

/**
 * Sequential run number within the CI project.
 * GitHub: $GITHUB_RUN_NUMBER
 * GitLab: $CI_PIPELINE_IID
 * CircleCI: $CIRCLE_BUILD_NUM
 * Azure DevOps: $BUILD_BUILDNUMBER
 */
export function detectRunNumber(): string | undefined {
  return (
    process.env['GITHUB_RUN_NUMBER'] ||
    process.env['CI_PIPELINE_IID'] ||
    process.env['CIRCLE_BUILD_NUM'] ||
    process.env['BUILD_BUILDNUMBER'] ||
    process.env['BITBUCKET_BUILD_NUMBER'] ||
    process.env['TRAVIS_BUILD_NUMBER'] ||
    process.env['BUILDKITE_BUILD_NUMBER'] ||
    undefined
  )
}

/**
 * Which attempt number this run is (1-based re-run counter).
 * GitHub: $GITHUB_RUN_ATTEMPT
 * GitLab: $CI_PIPELINE_RETRY_COUNT (not natively available, approximation)
 */
export function detectRunAttempt(): number | undefined {
  const v = process.env['GITHUB_RUN_ATTEMPT']
  return v ? parseInt(v, 10) : undefined
}

/**
 * Job name within the workflow.
 * GitHub: $GITHUB_JOB
 * GitLab: $CI_JOB_NAME
 */
export function detectJobName(): string | undefined {
  return process.env['GITHUB_JOB'] || process.env['CI_JOB_NAME'] || undefined
}

/**
 * Target / base branch of the pull request.
 * GitHub: $GITHUB_BASE_REF  (only set on pull_request events)
 * GitLab: $CI_MERGE_REQUEST_TARGET_BRANCH_NAME
 * Azure DevOps: $SYSTEM_PULLREQUEST_TARGETBRANCHNAME
 */
export function detectBaseBranch(): string | undefined {
  return (
    process.env['GITHUB_BASE_REF'] ||
    process.env['CI_MERGE_REQUEST_TARGET_BRANCH_NAME'] ||
    process.env['SYSTEM_PULLREQUEST_TARGETBRANCHNAME'] ||
    undefined
  )
}
