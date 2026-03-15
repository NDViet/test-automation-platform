/**
 * Detects CI environment variables to fill in git metadata automatically.
 * Supports: GitHub Actions, GitLab CI, Jenkins, CircleCI, Azure DevOps,
 *           Bitbucket Pipelines, TeamCity, Travis CI, Buildkite.
 */

export function detectBranch(): string | undefined {
  return (
    process.env['GITHUB_HEAD_REF'] ||           // GitHub PR branch
    process.env['GITHUB_REF_NAME'] ||           // GitHub push branch/tag
    process.env['CI_COMMIT_REF_NAME'] ||        // GitLab CI
    process.env['GIT_BRANCH'] ||                // Jenkins
    process.env['CIRCLE_BRANCH'] ||             // CircleCI
    process.env['BUILD_SOURCEBRANCH']?.replace('refs/heads/', '') || // Azure DevOps
    process.env['BITBUCKET_BRANCH'] ||          // Bitbucket
    process.env['TRAVIS_BRANCH'] ||             // Travis CI
    process.env['BUILDKITE_BRANCH'] ||          // Buildkite
    undefined
  )
}

export function detectCommitSha(): string | undefined {
  return (
    process.env['GITHUB_SHA'] ||
    process.env['CI_COMMIT_SHA'] ||
    process.env['GIT_COMMIT'] ||
    process.env['CIRCLE_SHA1'] ||
    process.env['BUILD_SOURCEVERSION'] ||       // Azure DevOps
    process.env['BITBUCKET_COMMIT'] ||
    process.env['TRAVIS_COMMIT'] ||
    process.env['BUILDKITE_COMMIT'] ||
    undefined
  )
}

export function detectCiRunUrl(): string | undefined {
  // GitHub Actions
  if (process.env['GITHUB_SERVER_URL'] && process.env['GITHUB_REPOSITORY'] && process.env['GITHUB_RUN_ID']) {
    return `${process.env['GITHUB_SERVER_URL']}/${process.env['GITHUB_REPOSITORY']}/actions/runs/${process.env['GITHUB_RUN_ID']}`
  }
  // GitLab CI
  if (process.env['CI_JOB_URL']) return process.env['CI_JOB_URL']
  // CircleCI
  if (process.env['CIRCLE_BUILD_URL']) return process.env['CIRCLE_BUILD_URL']
  // Azure DevOps
  if (process.env['SYSTEM_TEAMFOUNDATIONCOLLECTIONURI'] && process.env['BUILD_BUILDID']) {
    return `${process.env['SYSTEM_TEAMFOUNDATIONCOLLECTIONURI']}${process.env['SYSTEM_TEAMPROJECT']}/_build/results?buildId=${process.env['BUILD_BUILDID']}`
  }
  // Buildkite
  if (process.env['BUILDKITE_BUILD_URL']) return process.env['BUILDKITE_BUILD_URL']
  // Travis CI
  if (process.env['TRAVIS_BUILD_WEB_URL']) return process.env['TRAVIS_BUILD_WEB_URL']
  return undefined
}

export function isCI(): boolean {
  return !!(
    process.env['CI'] ||
    process.env['GITHUB_ACTIONS'] ||
    process.env['GITLAB_CI'] ||
    process.env['JENKINS_URL'] ||
    process.env['CIRCLECI'] ||
    process.env['TF_BUILD'] ||               // Azure DevOps
    process.env['BITBUCKET_BUILD_NUMBER'] ||
    process.env['TEAMCITY_VERSION'] ||
    process.env['TRAVIS'] ||
    process.env['BUILDKITE']
  )
}

export function detectCiProvider(): string {
  if (process.env['GITHUB_ACTIONS']) return 'github-actions'
  if (process.env['GITLAB_CI']) return 'gitlab-ci'
  if (process.env['JENKINS_URL']) return 'jenkins'
  if (process.env['CIRCLECI']) return 'circleci'
  if (process.env['TF_BUILD']) return 'azure-devops'
  if (process.env['BITBUCKET_BUILD_NUMBER']) return 'bitbucket'
  if (process.env['TEAMCITY_VERSION']) return 'teamcity'
  if (process.env['TRAVIS']) return 'travis-ci'
  if (process.env['BUILDKITE']) return 'buildkite'
  if (process.env['CI']) return 'ci'
  return 'local'
}
