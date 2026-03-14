export interface TestImpactResult {
  recommendedTests: string[]
  totalTests: number
  selectedTests: number
  estimatedReduction: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  uncoveredChangedClasses: string[]
  allChangedClasses: string[]
  junitFilter: string
  mavenFilter: string
  gradleFilter: string
}

export interface CoverageSummary {
  projectId: string
  mappedTests: number
  mappedClasses: number
  tiaEnabled: boolean
}

export interface OrgSummary {
  totalProjects: number
  totalRuns: number
  overallPassRate: number
  criticalFlakyTests: number
  projects: ProjectSummary[]
}

export interface ProjectSummary {
  projectId: string
  teamId: string
  passRate: number
  totalRuns: number
  flakyTests: number
}

export interface Team {
  id: string
  name: string
  slug: string
  createdAt: string
}

export interface Project {
  id: string
  name: string
  slug: string
  teamId: string
  teamName: string
  teamSlug: string
  createdAt: string
}

export interface ProjectDetail {
  project: Project | null
  flakiness: FlakinessItem[] | null
  qualityGate: QualityGateResult | null
  passRateTrend: PassRatePoint[] | null
  recentExecutions: ExecutionSummary[] | null
}

export interface ExecutionSummary {
  id: string
  runId: string
  projectId: string
  projectSlug: string
  projectName: string
  branch: string
  environment: string
  commitSha: string
  executionMode: string
  parallelism: number
  suiteName: string
  sourceFormat: string
  ciProvider: string
  ciRunUrl: string
  totalTests: number
  passed: number
  failed: number
  skipped: number
  broken: number
  durationMs: number
  passRate: number
  executedAt: string
  ingestedAt: string
}

export interface TestCase {
  id: string
  testId: string
  displayName: string
  className: string
  methodName: string
  tags: string[]
  status: 'PASSED' | 'FAILED' | 'SKIPPED' | 'BROKEN'
  durationMs: number
  failureMessage: string | null
  stackTrace: string | null
  retryCount: number
  createdAt: string
}

export interface FlakinessItem {
  id: string
  testId: string
  projectId: string
  score: number
  classification: 'STABLE' | 'WATCH' | 'FLAKY' | 'CRITICAL_FLAKY'
  totalRuns: number
  failureCount: number
  failureRate: number
  lastFailedAt: string | null
  lastPassedAt: string | null
}

export interface QualityGateResult {
  passed: boolean
  actualPassRate: number
  newFailures: number
  violations: string[]
}

export interface PassRatePoint {
  date: string
  passRate: number
  failureRate: number
  totalTests: number
  passed: number
  failed: number
}

export interface Alert {
  id: string
  ruleName: string
  severity: string
  message: string
  teamId: string
  projectId: string
  runId: string
  delivered: boolean
  firedAt: string
}

export interface ApiKey {
  id: string
  name: string
  prefix: string
  teamId: string
  expiresAt: string | null
  lastUsedAt: string | null
  createdAt: string
}

export interface FailureAnalysis {
  id: string
  testId: string
  projectId: string
  category: string
  confidence: number
  rootCause: string
  detailedAnalysis: string
  suggestedFix: string
  flakyCandidate: boolean
  analysedAt: string
}
