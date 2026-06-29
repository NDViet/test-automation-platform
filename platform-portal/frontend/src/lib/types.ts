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
  orgSlug: string
  passRate: number
  totalRuns: number
  flakyTests: number
}

export interface Organization {
  id: string
  name: string
  slug: string
  displayName?: string
  logoUrl?: string
  createdAt: string
}

/** Team — a sub-entity of a project (ADO-first: Org → Project → Team). */
export interface Team {
  id: string
  projectId: string
  name: string
  slug: string
  createdAt: string
}

export interface Project {
  id: string
  name: string
  slug: string
  orgId: string
  orgName: string
  orgSlug: string
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
  status: string
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
  hasTrace: boolean
  hasScreenshot: boolean
  hasVideo: boolean
  specFile: string | null
  browser: string | null
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

export interface LiteLlmModel {
  id: string
  label?: string
}

export interface AiSettings {
  enabled: boolean
  realtimeEnabled: boolean
  liteLlmBaseUrl: string
  liteLlmKeySet: boolean
  models: LiteLlmModel[]
  modelAnalysis: string
  modelStandard: string
  modelComplex: string
  modelSummarizer: string
}

export interface AiSettingsUpdate {
  enabled?: boolean
  realtimeEnabled?: boolean
  liteLlmBaseUrl?: string
  liteLlmApiKey?: string
  models?: LiteLlmModel[]
  modelAnalysis?: string
  modelStandard?: string
  modelComplex?: string
  modelSummarizer?: string
}

export interface TestConnectionResult {
  success: boolean
  message: string
  /** Model ids the gateway exposes for this key/team (from {baseUrl}/models). */
  models?: LiteLlmModel[]
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

export interface CreateOrganizationForm {
  name: string
  slug: string
}

export interface UpdateOrganizationForm {
  name?: string
  displayName?: string
}

export interface CreateTeamForm {
  name: string
  slug: string
}

export interface UpdateTeamForm {
  name?: string
}

export interface CreateProjectForm {
  orgId: string
  name: string
  slug: string
  repoUrl?: string
}

export interface UpdateProjectForm {
  name?: string
  description?: string
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
  /** Upstream creation date (ADO System.CreatedDate). */
  createdDate: string | null
  syncedAt: string | null
  updatedAt: string
  /** Link to open the original item in its source system (e.g. Azure DevOps), or null. */
  sourceUrl: string | null
}

export interface RequirementRef {
  id: string
  externalId: string | null
  title: string
  issueType: string
  status: string
  depth: number
  sourceUrl: string | null
}

export interface RequirementRelations {
  parent: RequirementRef | null
  children: RequirementRef[]
}

export interface RequirementStats {
  total: number
  byStatus: Record<string, number>
  byIssueType: Record<string, number>
}

// ── ADO org structure ──────────────────────────────────────────────────────────
export interface AdoTeam {
  id: string
  name: string
  slug: string | null
  description: string | null
  defaultAreaPath: string | null
  areaPaths: string[]
  memberCount: number
  syncedAt: string
}
export interface AdoArea {
  id: string
  path: string
  name: string
  slug: string | null
  parentPath: string | null
  hasChildren: boolean
  syncedAt: string
}
export interface AdoIteration {
  id: string
  path: string
  name: string
  parentPath: string | null
  startDate: string | null
  finishDate: string | null
  hasChildren: boolean
  syncedAt: string
}
export interface AdoUser {
  id: string
  uniqueName: string
  displayName: string | null
  email: string | null
  teamMember: boolean
  seenOnWorkItems: boolean
  qualityRole: string | null
  syncedAt: string
}
export interface AdoStructureSummary {
  teams: number
  areas: number
  iterations: number
  users: number
  qualityUsers: number
}

// ── Quality dashboards ─────────────────────────────────────────────────────────
export interface LabelValue {
  label: string
  value: number
}
export interface IterationStat {
  label: string
  total: number
  open: number
  done: number
}
export interface QualityOverview {
  totalDefects: number
  openDefects: number
  doneDefects: number
  blockedDefects: number
  createdLast30: number
  resolvedLast30: number
  qualityEngineers: number
  historyEvents: number
  byStatus: LabelValue[]
  byPriority: LabelValue[]
  bySeverity: LabelValue[]
  byArea: LabelValue[]
  byIteration: IterationStat[]
}
export interface EngineerStat {
  name: string
  role: string
  email: string | null
  defectsCreated: number
  createdByStatus: LabelValue[]
  defectsResolved: number
  openDefects: number
  otherTotal: number
  otherByStatus: LabelValue[]
  resolvedActual: number
  participated: number
  reopened: number
}
// ── Productivity / cycle time ──────────────────────────────────────────────────
export interface AreaProductivity {
  area: string
  wip: number
  overThreshold: number
  avgHours: number | null
  maxHours: number | null
}
export interface ProductivityOverview {
  thresholdHours: number
  totalWip: number
  totalOver: number
  areasAffected: number
  areas: AreaProductivity[]
}
export interface OverThresholdItem {
  id: string
  externalId: string | null
  title: string
  issueType: string
  status: string
  assignedTo: string | null
  areaPath: string | null
  cycleHours: number | null
  startedAt: string | null
  sourceUrl: string | null
}
export interface LeadAreaStat {
  area: string
  completed: number
  avgHours: number | null
  maxHours: number | null
}
export interface LeadOverview {
  totalCompleted: number
  avgHours: number | null
  maxHours: number | null
  areas: LeadAreaStat[]
}
export interface LeadItem {
  id: string
  externalId: string | null
  title: string
  issueType: string
  assignedTo: string | null
  areaPath: string | null
  leadHours: number | null
  createdDate: string | null
  completedAt: string | null
  sourceUrl: string | null
}

export interface ActivityEvent {
  externalId: string
  title: string | null
  issueType: string | null
  eventType: string
  fromValue: string | null
  toValue: string | null
  toCategory: string | null
  revisedAt: string | null
  sourceUrl: string | null
}
export interface QualityWorkItem {
  id: string
  externalId: string | null
  title: string
  issueType: string
  status: string
  priority: string | null
  areaPath: string | null
  iterationPath: string | null
  sourceUrl: string | null
}

export interface PagedRequirements {
  content: Requirement[]
  page: number
  size: number
  totalElements: number
  totalPages: number
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

/** A mapping ruleset at a scope (ORG/PROJECT), for the Mapping Suggester editor. */
export interface MappingRulesetView {
  scope: string
  customized: boolean // this scope has its own saved override
  source: 'PROJECT' | 'ORG' | 'DEFAULT' // where the shown rules currently come from
  json: string
  updatedBy: string | null
  updatedAt: string | null
}

/** A credential a project inherits from its organization (read-only, no secret). */
export interface InheritedCredential {
  integrationType: string
  scope: string
  displayName: string
  baseUrl: string | null
  connectionParams: Record<string, string>
  hasSecret: boolean
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
  parentId: string | null
  planType: string | null
  active: boolean
  areaPath: string | null
  teamId: string | null
  teamName: string | null
  selectionMode: string // STATIC | SMART
  filterIteration: string | null
  filterStatus: string | null
  filterTags: string | null
  caseCount: number
  createdAt: string
  updatedAt: string
}

export interface Environment {
  id: string
  projectId: string
  name: string
  description: string | null
  properties: Record<string, string>
  createdAt: string
}

export interface CaseProperty {
  name: string
  value: string
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
  updatedBy: string
  agentSessionId: string | null
  sourceRequirementId: string | null
  acRefs: string[]
  linkedRequirementIds: string[]
  automationStatus: string
  automationPrUrl: string | null
  automationWorkflowId: string | null
  hasAutomation: boolean
  lastUpdatedByAnalysisId: string | null
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
  releaseId: string | null
  releaseName: string | null
  iterationPath: string | null
  areaPath: string | null
  teamId: string | null
  teamName: string | null
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
  defectId: string | null
  defectUrl: string | null
  defectTitle: string | null
  defectState: string | null
}

export interface ExecutionAttachment {
  id: string
  executionId: string
  fileName: string
  contentType: string | null
  sizeBytes: number
  uploadedBy: string | null
  uploadedAt: string
}

export interface SelectableTestCase {
  id: string
  externalId: string | null
  title: string
  priority: string
  status: string
  requirementExternalIds: string[]
}

export interface CreateTestCaseForm {
  title: string
  description?: string
  preconditions?: string
  expectedResult?: string
  priority?: string
  suiteId?: string
  sourceRequirementId?: string
  linkedRequirementIds?: string[]
  acRefs?: string[]
  steps?: { action: string; expectedResult?: string; notes?: string }[]
}

export interface ApplySuggestionForm {
  analysisId: string
  title?: string
  description?: string
  expectedResult?: string
  steps?: { action: string; expectedResult?: string; notes?: string }[]
}

export interface CreateTestRunForm {
  name: string
  releaseVersion?: string
  environment?: string
  triggeredBy?: string
  testCaseIds: string[]
  environmentId?: string // named Environment (V50)
  matrixType?: 'FULL' | 'PAIRWISE' // parametrized expansion mode
  suiteIds?: string[] // reusable suites; their resolved cases are unioned in
  releaseId?: string // monitoring dimension: platform release
  iterationPath?: string // monitoring dimension: ADO sprint
  areaPath?: string // monitoring dimension: ADO area
  teamId?: string // monitoring dimension: ADO team
}

// ── Releases ───────────────────────────────────────────────────────────────────
export interface Release {
  id: string
  projectId: string
  name: string
  releaseType: string
  externalId: string | null
  targetDate: string | null
  state: string
  // composite mapping (any subset; AND-combined)
  mapIterationPath: string | null
  mapAreaPath: string | null
  mapTeamId: string | null
  mapTeamName: string | null
  mapTag: string | null
  mappingField: string | null
  mappingValue: string | null
  mappedRequirementCount: number
  linkedRunCount: number
  createdAt: string
}

export interface CreateReleaseForm {
  name: string
  releaseType?: string
  externalId?: string
  targetDate?: string
  state?: string
  mapIterationPath?: string
  mapAreaPath?: string
  mapTeamId?: string
  mapTag?: string
  mappingField?: string
  mappingValue?: string
}

// ── Test Execution release board ────────────────────────────────────────────────
export interface ReleaseCard {
  releaseId: string
  releaseName: string
  state: string
  iterationPath: string | null
  areaPath: string | null
  teamId: string | null
  teamName: string | null
  runs: number
  total: number
  passed: number
  failed: number
  blocked: number
  skipped: number
  pending: number
  passRate: number
  executedPct: number
  mappedReqs: number
  coveredReqs: number
  coveragePct: number
  lastExecutedAt: string | null
}

export interface TeamReleaseGroup {
  teamId: string | null
  teamName: string
  releases: ReleaseCard[]
}

export interface ReleaseBoard {
  iterations: string[]
  groups: TeamReleaseGroup[]
  releaseCount: number
  runs: number
  total: number
  passed: number
  passRate: number
  coveragePct: number
}

// ── Test Execution monitor ──────────────────────────────────────────────────────
export interface ExecDimensionGroup {
  key: string | null
  label: string
  runs: number
  total: number
  passed: number
  failed: number
  blocked: number
  skipped: number
  pending: number
  passRate: number
  executedPct: number
  lastExecutedAt: string | null
}

export interface ExecMonitorOverview {
  dimension: string
  runs: number
  total: number
  passed: number
  failed: number
  blocked: number
  skipped: number
  pending: number
  passRate: number
  executedPct: number
  groups: ExecDimensionGroup[]
}

export interface ExecRunSummary {
  id: string
  name: string
  status: string
  total: number
  passed: number
  failed: number
  blocked: number
  skipped: number
  pending: number
  createdAt: string
}

export interface CreateTestSuiteForm {
  name: string
  description?: string
  parentId?: string | null
  planType?: string
  active?: boolean
  areaPath?: string
  teamId?: string
  selectionMode?: string // STATIC | SMART
  filterIteration?: string
  filterStatus?: string
  filterTags?: string
}

export interface CoverageRow {
  requirementId: string
  externalId: string | null
  title: string
  issueType: string | null
  requirementStatus: string | null
  areaPath: string | null
  teamName: string | null
  automatedCases: number
  manualCases: number
  lastStatus: string | null
}

export interface CoverageGroup {
  label: string
  total: number
  covered: number
  coveredByAutomation: number
  manualOnly: number
  uncovered: number
  coveragePct: number
  automationPct: number
}

export interface CoverageReport {
  totalRequirements: number
  coveredByAutomation: number
  coveredManualOnly: number
  uncovered: number
  automationCoveragePct: number
  byArea: CoverageGroup[]
  byTeam: CoverageGroup[]
  requirements: CoverageRow[]
}

// ── Schema drift (upstream ↔ platform) ────────────────────────────────────────
export interface DriftFieldChange {
  referenceName: string
  name: string
  type: string
  mapped: boolean
}
export interface DriftTypeChange {
  referenceName: string
  name: string
  fromType: string
  toType: string
}
export interface SchemaDriftReport {
  workItemType: string
  hasBaseline: boolean
  justCaptured: boolean
  hasDrift: boolean
  baselineCapturedAt: string | null
  removed: DriftFieldChange[]
  added: DriftFieldChange[]
  typeChanged: DriftTypeChange[]
  removedStateCategories: string[]
  addedStateCategories: string[]
}

// ── ADO schema discovery + mapping suggestion ─────────────────────────────────
export interface AdoProject {
  id: string
  name: string
}
export interface AdoTypeSummary {
  name: string
  custom: boolean
  suggestedLane: string // REQUIREMENT | DEFECT | IGNORE
  suggestedIssueType: string | null
}
export interface AdoFieldInfo {
  referenceName: string
  name: string
  type: string
  custom: boolean
  required: boolean
}
export interface AdoStateInfo {
  name: string
  category: string
  color: string
}
export interface AdoTypeSchema {
  workItemType: string
  fields: AdoFieldInfo[]
  states: AdoStateInfo[]
  suggestedProfile: unknown // MappingProfile JSON (apiVersion/kind/metadata/spec)
}

// ── RBAC ──────────────────────────────────────────────────────────────────────
export type PlatformRole = 'ORG_ADMIN' | 'TEAM_ADMIN' | 'TEAM_MEMBER' | 'VIEWER'

export interface TeamMemberAssignment {
  id: string
  userId: string
  teamId: string | null // null = org-wide
  role: PlatformRole
  grantedBy: string | null
  grantedAt: string
}

export interface GrantRoleForm {
  userId: string
  scope: 'ORG' | 'TEAM'
  teamId?: string | null
  role: PlatformRole
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

// ── Review Queue / Work Items ─────────────────────────────────────────────────

export type WorkItemType =
  | 'TEST_CASE_REVIEW'
  | 'AUTOMATION_PR'
  | 'AGENT_REVIEW'
  | 'WORKFLOW'
  | 'IMPACT_ANALYSIS'

export interface WorkItem {
  id: string
  itemType: WorkItemType
  status: string
  title: string
  description: string
  actionUrl: string | null
  createdAt: string
  metadata: Record<string, unknown>
}

// ── Integration Credentials (Admin PAT cascade) ──────────────────────────────
export type CredentialScope = 'ORG' | 'TEAM' | 'PROJECT'

export interface GithubRepo {
  fullName: string
  owner: string | null
  name: string | null
  isPrivate: boolean
  defaultBranch: string | null
  htmlUrl: string | null
  managed: boolean
}

export interface AzureOrg {
  accountName: string
  accountId: string | null
  accountUri: string | null
  managed: boolean
}

/** Credential encryption key lifecycle status. */
export interface CredKeyStatus {
  /** Key came from the PLATFORM_CRED_KEY env var (passphrase flow disabled). */
  envProvided: boolean
  /** A passphrase has been set up (a settings row exists). */
  initialized: boolean
  /** A usable key is currently loaded — credentials can be saved/used. */
  unlocked: boolean
}

/** Result of the first-run ADO bootstrap (POST /ado/onboard/org). */
export interface AdoOnboardResult {
  org: {
    organizationId: string
    slug: string
    credentialId: string
    orgCreated: boolean
  }
  projects: { created: number; total: number }
  structure: { projectsSynced: number; teamsCreated: number; failures: string[] }
  members: { grantsCreated: number; membersSeen: number; ownerEmail: string | null }
}

/** Result of an on-demand ADO re-sync (POST /ado/onboard/resync). */
export interface AdoResyncResult {
  projects: { created: number; total: number }
  structure: { projectsSynced: number; teamsCreated: number; failures: string[] }
  members: { grantsCreated: number; membersSeen: number; ownerEmail: string | null }
}

/** The PAT owner's identity resolved from Azure DevOps (GET /ado/onboard/me). */
export interface AdoOwnerProfile {
  email: string | null
  displayName: string | null
  id: string | null
}

/** Result of claiming org admin from the PAT owner (POST /ado/onboard/claim-admin). */
export interface AdoClaimAdminResult {
  email: string
  displayName: string | null
  granted: boolean
  alreadyAdmin: boolean
}

export interface Credential {
  id: string
  scope: CredentialScope
  scopeId: string | null
  integrationType: string
  displayName: string
  baseUrl: string | null
  connectionParams: Record<string, string>
  hasSecret: boolean
  enabled: boolean
  syncIntervalMinutes: number
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

// ── GitHub Actions workflows ──────────────────────────────────────────────────

export interface GitHubWorkflow {
  id: number
  name: string
  path: string
  state: 'active' | 'disabled_manually' | 'disabled_inactivity' | string
  htmlUrl: string
  repoFullName: string
}

export interface GitHubWorkflowRun {
  id: number
  displayTitle: string
  status: 'queued' | 'in_progress' | 'completed' | string
  conclusion:
    | 'success'
    | 'failure'
    | 'cancelled'
    | 'skipped'
    | 'timed_out'
    | 'action_required'
    | null
  branch: string
  event: string
  htmlUrl: string
  createdAt: string | null
  updatedAt: string | null
  headSha: string
  repoFullName: string
  workflowId: number
}

export interface WorkflowDispatchResult {
  triggered: boolean
  message: string
}

export interface SaveCredentialForm {
  scope: CredentialScope
  scopeId?: string | null
  integrationType: string
  displayName: string
  baseUrl?: string
  connectionParams?: Record<string, string>
  secret?: Record<string, string>
  enabled?: boolean
}

// ── Unified Test Execution list ───────────────────────────────────────────────

export interface UnifiedExecutionItem {
  id: string
  /** "MANUAL" | "AUTOMATED" */
  type: 'MANUAL' | 'AUTOMATED'
  name: string
  status: string
  environment: string
  // Automated-only
  ciProvider: string | null
  branch: string | null
  commitSha: string | null
  workflow: string | null
  triggerType: string | null
  // Counts
  totalTests: number
  passed: number
  failed: number
  blocked: number // manual: BLOCKED executions; automated: 0
  skipped: number
  pending: number // manual: PENDING executions; automated: 0
  broken: number // automated only (timedOut / interrupted)
  passRate: number // 0–1
  durationMs: number
  // Scope dimensions
  teamId: string | null
  teamName: string | null
  areaPath: string | null
  iterationPath: string | null
  // Manual-only
  releaseId: string | null
  releaseName: string | null
  releaseVersion: string | null
  triggeredBy: string | null
  // Automated-only
  ciRunUrl: string | null
  /** Navigation key: MANUAL → TestRun UUID, AUTOMATED → CI run ID */
  runId: string
  date: string
}

// ── GitHub repo cache + project assignments ───────────────────────────────────

export interface GitHubCachedRepo {
  fullName: string
  owner: string | null
  name: string | null
  isPrivate: boolean
  defaultBranch: string | null
  htmlUrl: string | null
  managed: boolean
}

export interface GitHubCacheResult {
  repos: GitHubCachedRepo[]
  syncedAt: string | null
  totalCount: number
}

export interface ProjectRepoAssignment {
  id: string
  repoFullName: string
  role: RepoType
  credentialId: string
  owner: string | null
  name: string | null
  htmlUrl: string | null
  isPrivate: boolean
}

// ── Automated Test Analytics ─────────────────────────────────────────────────

export interface AutomatedTestSummary {
  testId: string
  displayName: string
  /** className — suite/describe block name for Playwright results */
  suiteName: string | null
  tags: string[]
  totalRuns: number
  passed: number
  failed: number
  skipped: number
  broken: number
  passRate: number
  failRate: number
  lastStatus: string
  lastRunId: string | null
  lastRunAt: string
  avgDurationMs: number
  /** Spec file path relative to project root. Null for results ingested before V13. */
  specFile: string | null
  /** Distinct Playwright project names (browsers/devices) seen in the time window. */
  browsers: string[]
  /** Distinct Playwright annotation types seen (fixme, slow, fail, skip, custom). */
  annotationTypes: string[]
  /** Per-label-key, distinct values seen. e.g. {owner: ["alice"], jira: ["PROJ-123"]} */
  labelMap: Record<string, string[]>
  hasScreenshot: boolean
  hasVideo: boolean
}

export interface TestTrendPoint {
  date: string
  total: number
  passed: number
  failed: number
  skipped: number
  passRate: number
  avgDurationMs: number | null
}

export interface RecentRun {
  runId: string | null
  resultId: string | null
  status: string
  runAt: string
  durationMs: number | null
  failureMessage: string | null
  environment: string | null
  branch: string | null
  hasTrace: boolean
  browser: string | null
  specFile: string | null
  hasScreenshot: boolean
  hasVideo: boolean
}

export interface AutomatedTestDetail {
  trend: TestTrendPoint[]
  recentRuns: RecentRun[]
}

// ── AI generation skills (project-scoped reusable instruction sets) ──────────
export interface AiSkill {
  id: string
  projectId: string
  name: string
  description: string | null
  instructions: string
  enabled: boolean
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

export interface AiSkillForm {
  name: string
  description?: string
  instructions: string
  enabled: boolean
}

// ── AI prompt templates (project-scoped SYSTEM/USER templates) ───────────────
export type PromptKind = 'SYSTEM' | 'USER'

export interface AiPromptTemplate {
  id: string
  projectId: string
  kind: PromptKind
  name: string
  body: string
  isDefault: boolean
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

export interface AiPromptTemplateForm {
  kind: PromptKind
  name: string
  body: string
  isDefault: boolean
}

export interface PromptDefaults {
  system: string
  user: string
}

// ── Agents (org/project-scoped reusable agent configs) ───────────────────────
export type AgentScope = 'orgs' | 'projects'

export interface Agent {
  id: string
  scope: 'ORG' | 'PROJECT'
  scopeId: string
  name: string
  description: string | null
  persona: string | null
  systemTemplateId: string | null
  userTemplateId: string | null
  skillIds: string[]
  modelRole: string | null
  modelId: string | null
  contextConfig: Record<string, unknown> | null
  maxRounds: number
  enabled: boolean
  inherited: boolean
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

export interface AgentForm {
  name: string
  description?: string | null
  persona?: string | null
  systemTemplateId?: string | null
  userTemplateId?: string | null
  skillIds: string[]
  modelRole?: string | null
  modelId?: string | null
  contextConfig?: Record<string, unknown> | null
  maxRounds?: number
  enabled?: boolean
}

export interface TaskAgentAssignment {
  id: string
  scope: 'ORG' | 'PROJECT'
  scopeId: string
  taskType: string
  subType: string
  agentId: string
  enabled: boolean
}

export interface TaskSubType {
  taskType: string
  key: string
  label: string
  isDefault: boolean
}

export interface EffectiveAssignment {
  source: 'PROJECT' | 'ORG' | 'SEED'
  agentId: string | null
  agentName: string
}

// ── AI test-case generation request/result ──────────────────────────────────
export interface GenerationFile {
  id: string
  projectId: string
  fileName: string
  contentType: string | null
  sizeBytes: number
  uploadedBy: string | null
  uploadedAt: string
}

export interface GenerateTestCasesRequestBody {
  requirementIds?: string[]
  freeText?: string
  fileIds?: string[]
  skillIds?: string[]
  systemPromptOverride?: string
  userPromptOverride?: string
  maxRounds?: number
  agentId?: string
  subType?: string
}

export interface GenerationStartResult {
  workflowId: string
  projectId?: string
  message: string
}

export interface ClarificationQuestion {
  id: string
  question: string
  kind?: 'TEXT' | 'CHOICE'
  options?: string[]
}

export interface ClarificationAnswer {
  id: string
  answer: string
}

export interface ClarificationRound {
  round: number
  status: string
  questions: ClarificationQuestion[] | null
  answers: ClarificationAnswer[] | null
}

export interface GenerationStatus {
  workflowId: string
  status: string
  rounds: ClarificationRound[]
  pending: ClarificationRound | null
}
