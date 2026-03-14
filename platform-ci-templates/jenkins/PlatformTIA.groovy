/**
 * platform-ci-templates/jenkins/PlatformTIA.groovy
 *
 * Jenkins Shared Library step for Test Impact Analysis.
 *
 * Setup:
 *   1. Add this file to your Jenkins shared library at vars/platformTIA.groovy
 *   2. Configure the library in Jenkins → Manage → Configure System → Global Pipeline Libraries
 *
 * Usage in Jenkinsfile:
 *
 *   @Library('platform-shared-lib') _
 *
 *   pipeline {
 *     agent any
 *     environment {
 *       PLATFORM_API_KEY = credentials('platform-api-key')  // Jenkins credential
 *     }
 *     stages {
 *       stage('Test Impact Analysis') {
 *         steps {
 *           script {
 *             def tia = platformTIA(
 *               platformUrl:  'http://platform:8082',
 *               projectId:    env.PLATFORM_PROJECT_ID,
 *               apiKey:       env.PLATFORM_API_KEY,
 *               baseRef:      'origin/main'
 *             )
 *             if (tia.riskLevel == 'CRITICAL' || tia.selectedTests == 0) {
 *               sh 'mvn test'
 *             } else {
 *               sh "mvn test -Dtest=\"${tia.mavenFilter}\""
 *             }
 *           }
 *         }
 *       }
 *     }
 *   }
 */
def call(Map config = [:]) {
    def platformUrl = config.platformUrl ?: env.PLATFORM_URL
    def projectId   = config.projectId   ?: env.PLATFORM_PROJECT_ID
    def apiKey      = config.apiKey      ?: env.PLATFORM_API_KEY
    def baseRef     = config.baseRef     ?: 'origin/main'
    def maxFiles    = config.maxFiles    ?: 200

    if (!platformUrl || !projectId) {
        echo "[TIA] PLATFORM_URL or projectId not configured — skipping Test Impact Analysis"
        return [riskLevel: 'CRITICAL', selectedTests: 0, totalTests: 0,
                mavenFilter: '', gradleFilter: '', estimatedReduction: '0%']
    }

    // Fetch base branch history (shallow clone may not have it)
    sh "git fetch origin ${baseRef.replace('origin/', '')} --depth=1 || true"

    def changedFiles = sh(
        script: """
            git diff --name-only ${baseRef} HEAD \\
            | grep -E '\\.(java|kt|scala|ts|tsx|js|jsx|py|rb|go|cs)\$' \\
            | head -${maxFiles}
        """,
        returnStdout: true
    ).trim()

    if (!changedFiles) {
        echo "[TIA] No source files changed — recommend running full suite"
        return [riskLevel: 'LOW', selectedTests: 0, totalTests: 0,
                mavenFilter: '', gradleFilter: '', estimatedReduction: '0%',
                reason: 'no_changes']
    }

    def params = changedFiles.split('\n').collect { "changedFiles=${URLEncoder.encode(it, 'UTF-8')}" }.join('&')
    def authHeader = apiKey ? "-H 'X-API-Key: ${apiKey}'" : ''

    def response = sh(
        script: """
            curl -sf ${authHeader} \\
              "${platformUrl}/api/v1/analytics/${projectId}/impact?${params}" \\
              || echo '{}'
        """,
        returnStdout: true
    ).trim()

    def json = readJSON(text: response)

    def result = [
        recommendedTests:  json.recommendedTests  ?: [],
        selectedTests:     json.selectedTests     ?: 0,
        totalTests:        json.totalTests        ?: 0,
        estimatedReduction: json.estimatedReduction ?: '0%',
        riskLevel:         json.riskLevel         ?: 'CRITICAL',
        mavenFilter:       json.mavenFilter       ?: '',
        gradleFilter:      json.gradleFilter      ?: '',
        uncoveredClasses:  json.uncoveredChangedClasses ?: [],
    ]

    echo "[TIA] Selected ${result.selectedTests}/${result.totalTests} tests " +
         "(${result.estimatedReduction} reduction) risk=${result.riskLevel}"

    if (result.uncoveredClasses) {
        echo "[TIA] Warning: ${result.uncoveredClasses.size()} changed classes have no coverage mappings"
    }

    return result
}
