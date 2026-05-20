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
  computedAt?: string | null
}

export interface FlakyFixResult {
  workflowId: string
  testId: string
  message: string
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

export interface AiSettings {
  enabled: boolean
  realtimeEnabled: boolean
  provider: 'anthropic' | 'openai'
  model: string
  anthropicKeySet: boolean
  openaiKeySet: boolean
}

export interface AiSettingsUpdate {
  enabled?: boolean
  realtimeEnabled?: boolean
  provider?: string
  model?: string
  anthropicApiKey?: string
  openaiApiKey?: string
}

export interface TestConnectionResult {
  success: boolean
  message: string
}

export interface AnalyseNowResult {
  queued: number
  hours: number
}

export type RepoType = 'GENERAL' | 'CODEBASE' | 'TEST_AUTOMATION'

export interface IntegrationConfig {
  id: string
  projectId: string
  integrationType: string
  displayName: string | null
  syncDirection: string
  repoType: RepoType
  connectionParams: Record<string, string>
  fieldMappings: Record<string, unknown>
  filterConfig: Record<string, string>
  enabled: boolean
  lastSyncedAt: string | null
  consecutiveErrors: number
}

export interface CreateTeamForm {
  name: string
  slug: string
}

export interface UpdateTeamForm {
  name?: string
}

export interface CreateProjectForm {
  teamId: string
  name: string
  slug: string
  repoUrl?: string
}

export interface UpdateProjectForm {
  name?: string
  repoUrl?: string
}

export interface Requirement {
  id: string
  projectId: string
  externalId: string | null
  title: string
  description: string | null
  issueType: string
  status: string
  priority: string | null
  depth: number
  parentId: string | null
  acceptanceCriteria: unknown[]
  changeSummary: string | null
  syncedAt: string | null
  updatedAt: string
}

export interface RequirementStats {
  total: number
  byStatus: Record<string, number>
  byIssueType: Record<string, number>
}

export interface PrAnalysis {
  workflowId: string
  projectId: string
  refUrl: string | null
  status: string
  summary: string | null
  totalInputTokens: number
  totalOutputTokens: number
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface SaveIntegrationConfigForm {
  id?: string
  integrationType: string
  displayName?: string
  syncDirection?: string
  repoType?: RepoType
  connectionParams: Record<string, string>
  fieldMappings?: Record<string, unknown>
  filterConfig?: Record<string, string>
  enabled: boolean
}

// ── Test Case Management ──────────────────────────────────────────────────────

export interface TestSuite {
  id: string
  projectId: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface TestCaseStep {
  id: string
  stepNumber: number
  action: string
  expectedResult: string | null
  notes: string | null
}

export interface ManagedTestCase {
  id: string
  projectId: string
  suiteId: string | null
  externalId: string | null
  title: string
  description: string | null
  preconditions: string | null
  expectedResult: string | null
  priority: string
  status: string
  coverageStatus: string
  createdBy: string
  agentSessionId: string | null
  sourceRequirementId: string | null
  acRefs: string[]
  automationStatus: string
  automationPrUrl: string | null
  hasAutomation: boolean
  lastResult: string | null
  lastExecutedAt: string | null
  steps: TestCaseStep[]
  createdAt: string
  updatedAt: string
}

export interface TestRun {
  id: string
  projectId: string
  name: string
  releaseVersion: string | null
  environment: string
  status: string
  triggeredBy: string | null
  totalTests: number
  passed: number
  failed: number
  blocked: number
  skipped: number
  pending: number
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export interface TestCaseExecution {
  id: string
  testRunId: string
  testCaseId: string
  testCaseTitle: string
  status: string
  actualResult: string | null
  notes: string | null
  executedBy: string | null
  executedAt: string | null
  createdAt: string
}

export interface CreateTestCaseForm {
  title: string
  description?: string
  preconditions?: string
  expectedResult?: string
  priority?: string
  suiteId?: string
  sourceRequirementId?: string
  acRefs?: string[]
  steps?: { action: string; expectedResult?: string; notes?: string }[]
}

export interface CreateTestRunForm {
  name: string
  releaseVersion?: string
  environment?: string
  triggeredBy?: string
  testCaseIds: string[]
}

// ── Impact Analyses ───────────────────────────────────────────────────────────

export interface ImpactAnalysisSuggestion {
  type: 'UPDATE_MANUAL_TEST' | 'CREATE_AUTOMATED_TEST' | 'UPDATE_AUTOMATION'
  title: string
  reason: string
  details: string
  testCaseId: string | null
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
}

export interface ImpactAnalysisResult {
  summary: string
  suggestions: ImpactAnalysisSuggestion[]
}

export interface LinkedPr {
  repoFullName: string
  prNumber: number
  prUrl: string
  prTitle: string
}

export interface ImpactAnalysis {
  id: string
  projectId: string
  name: string
  status: 'DRAFT' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  linkedPrs: LinkedPr[]
  linkedRequirementIds: string[]
  suggestions: ImpactAnalysisResult | null
  summary: string | null
  workflowId: string | null
  createdAt: string
  updatedAt: string
}

export interface CodabasePr {
  number: number
  title: string
  html_url: string
  state: string
  user: string
  updated_at: string
  head_ref: string
  base_ref: string
  body: string | null
  repoFullName: string
}

export interface CreateImpactAnalysisForm {
  name: string
  linkedPrs: LinkedPr[]
  linkedRequirementIds: string[]
}
