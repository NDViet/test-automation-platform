const BASE = '/api/portal'

async function get<T>(path: string): Promise<T> {
  const res = await fetch(BASE + path)
  if (!res.ok) throw new Error(`GET ${path} → ${res.status}`)
  return res.json() as Promise<T>
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`POST ${path} → ${res.status}`)
  return res.json() as Promise<T>
}

async function put<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`PUT ${path} → ${res.status}`)
  return res.json() as Promise<T>
}

async function del(path: string): Promise<void> {
  const res = await fetch(BASE + path, { method: 'DELETE' })
  if (!res.ok) throw new Error(`DELETE ${path} → ${res.status}`)
}

export const api = {
  overview: (days = 7) => get<{ summary: import('./types').OrgSummary; recentAlerts: import('./types').Alert[] }>(`/overview?days=${days}`),
  teams: () => get<import('./types').Team[]>('/teams'),
  projects: (teamSlug?: string) => get<import('./types').Project[]>(teamSlug ? `/projects?teamSlug=${teamSlug}` : '/projects'),
  projectDetail: (id: string, days = 7) => get<import('./types').ProjectDetail>(`/projects/${id}?days=${days}`),
  executions: (projectId: string, limit = 20) => get<import('./types').ExecutionSummary[]>(`/projects/${projectId}/executions?limit=${limit}`),
  passRateTrend: (projectId: string, days = 30) => get<import('./types').PassRatePoint[]>(`/projects/${projectId}/trends/pass-rate?days=${days}`),
  flakiness: (projectId: string, limit = 20, classification?: string) => {
    let u = `/projects/${projectId}/flakiness?limit=${limit}`
    if (classification) u += `&classification=${classification}`
    return get<import('./types').FlakinessItem[]>(u)
  },
  qualityGate: (projectId: string) => get<import('./types').QualityGateResult>(`/projects/${projectId}/quality-gate`),
  analyses: (projectId: string, days = 7) => get<import('./types').FailureAnalysis[]>(`/projects/${projectId}/analyses?days=${days}`),
  runDetail: (runId: string) => get<{ summary: import('./types').ExecutionSummary; testCases: import('./types').TestCase[] }>(`/executions/${runId}`),
  alerts: (days = 7) => get<import('./types').Alert[]>(`/alerts?days=${days}`),
  apiKeys: (teamId: string) => get<import('./types').ApiKey[]>(`/api-keys?teamId=${teamId}`),
  createApiKey: (body: { name: string; teamId: string; ttlDays?: number }) => post<{ key: string; id: string; name: string }>('/api-keys', body),
  revokeApiKey: (id: string) => del(`/api-keys/${id}`),
  impactSummary: (projectId: string) => get<import('./types').CoverageSummary>(`/projects/${projectId}/impact/summary`),
  testImpact: (projectId: string, changedFiles: string[]) => {
    const params = changedFiles.map(f => `changedFiles=${encodeURIComponent(f)}`).join('&')
    return get<import('./types').TestImpactResult>(`/projects/${projectId}/impact?${params}`)
  },
  aiSettings: () => get<import('./types').AiSettings>('/ai/settings'),
  updateAiSettings: (body: import('./types').AiSettingsUpdate) => put<import('./types').AiSettings>('/ai/settings', body),
  testAiConnection: (body: { provider?: string; model?: string; apiKey?: string }) => post<import('./types').TestConnectionResult>('/ai/settings/test', body),
  analyseResult: (projectId: string, resultId: string) => post<import('./types').FailureAnalysis>(`/ai/projects/${projectId}/results/${resultId}/analyse`, {}),
  analyseNow: (hours = 24) => post<import('./types').AnalyseNowResult>(`/ai/analyse/run-now?hours=${hours}`, {}),
  requirements: (projectId: string, params?: { status?: string; issueType?: string; search?: string }) => {
    const q = new URLSearchParams()
    if (params?.status)    q.set('status', params.status)
    if (params?.issueType) q.set('issueType', params.issueType)
    if (params?.search)    q.set('search', params.search)
    const qs = q.toString()
    return get<import('./types').Requirement[]>(`/projects/${projectId}/requirements${qs ? '?' + qs : ''}`)
  },
  requirementStats:  (projectId: string)             => get<import('./types').RequirementStats>(`/projects/${projectId}/requirements/stats`),
  requirementDetail: (projectId: string, reqId: string) => get<import('./types').Requirement>(`/projects/${projectId}/requirements/${reqId}`),
  prAnalyses:        (projectId: string, limit = 30) => get<import('./types').PrAnalysis[]>(`/projects/${projectId}/pr-analyses?limit=${limit}`),
  impactAnalyses:    (projectId: string) => get<import('./types').ImpactAnalysis[]>(`/projects/${projectId}/impact-analyses`),
  impactAnalysis:    (projectId: string, id: string) => get<import('./types').ImpactAnalysis>(`/projects/${projectId}/impact-analyses/${id}`),
  codebasePrs:       (projectId: string) => get<import('./types').CodabasePr[]>(`/projects/${projectId}/impact-analyses/prs`),
  createImpactAnalysis: (projectId: string, body: import('./types').CreateImpactAnalysisForm) =>
    post<import('./types').ImpactAnalysis>(`/projects/${projectId}/impact-analyses`, body),
  createTeam: (body: import('./types').CreateTeamForm) => post<import('./types').Team>('/teams', body),
  updateTeam: (id: string, body: import('./types').UpdateTeamForm) => put<import('./types').Team>(`/teams/${id}`, body),
  deleteTeam: (id: string) => del(`/teams/${id}`),
  createProject: (body: import('./types').CreateProjectForm) => post<import('./types').Project>('/projects', body),
  updateProject: (id: string, body: import('./types').UpdateProjectForm) => put<import('./types').Project>(`/projects/${id}`, body),
  deleteProject: (id: string) => del(`/projects/${id}`),
  integrations: (projectId: string) => get<import('./types').IntegrationConfig[]>(`/projects/${projectId}/integrations`),
  saveIntegration: (projectId: string, body: import('./types').SaveIntegrationConfigForm) => post<import('./types').IntegrationConfig>(`/projects/${projectId}/integrations`, body),
  deleteIntegration: (projectId: string, configId: string) => del(`/projects/${projectId}/integrations/${configId}`),
  syncIntegrations: (projectId: string) => post<Record<string, unknown>>(`/projects/${projectId}/integrations/sync`, {}),

  // Test Suites
  testSuites: (projectId: string) => get<import('./types').TestSuite[]>(`/projects/${projectId}/test-suites`),
  createTestSuite: (projectId: string, body: { name: string; description?: string }) => post<import('./types').TestSuite>(`/projects/${projectId}/test-suites`, body),
  updateTestSuite: (projectId: string, suiteId: string, body: { name: string; description?: string }) => put<import('./types').TestSuite>(`/projects/${projectId}/test-suites/${suiteId}`, body),
  deleteTestSuite: (projectId: string, suiteId: string) => del(`/projects/${projectId}/test-suites/${suiteId}`),

  // Test Cases
  testCases: (projectId: string, params?: { status?: string; suiteId?: string; search?: string }) => {
    const q = new URLSearchParams()
    if (params?.status)  q.set('status', params.status)
    if (params?.suiteId) q.set('suiteId', params.suiteId)
    if (params?.search)  q.set('search', params.search)
    const qs = q.toString()
    return get<import('./types').ManagedTestCase[]>(`/projects/${projectId}/test-cases${qs ? '?' + qs : ''}`)
  },
  testCase: (projectId: string, tcId: string) => get<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}`),
  createTestCase: (projectId: string, body: import('./types').CreateTestCaseForm) => post<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases`, body),
  updateTestCase: (projectId: string, tcId: string, body: import('./types').CreateTestCaseForm) => put<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}`, body),
  deleteTestCase: (projectId: string, tcId: string) => del(`/projects/${projectId}/test-cases/${tcId}`),
  replaceSteps: (projectId: string, tcId: string, steps: { action: string; expectedResult?: string; notes?: string }[]) => put<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}/steps`, steps),
  submitForReview: (projectId: string, tcId: string) => post<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}/submit-review`, {}),
  approveTestCase: (projectId: string, tcId: string) => post<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}/approve`, {}),
  rejectTestCase: (projectId: string, tcId: string) => post<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}/reject`, {}),
  triggerAutomation: (projectId: string, tcId: string, githubConfigId?: string) => {
    const q = githubConfigId ? `?githubConfigId=${githubConfigId}` : ''
    return post<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}/generate-automation${q}`, {})
  },
  generateTestCasesFromAI: (projectId: string, requirementIds?: string[]) =>
    post<{ workflowId: string; message: string }>(`/projects/${projectId}/test-cases/generate`, { requirementIds }),

  // Test Runs
  testRuns: (projectId: string) => get<import('./types').TestRun[]>(`/projects/${projectId}/test-runs`),
  testRun: (projectId: string, runId: string) => get<import('./types').TestRun>(`/projects/${projectId}/test-runs/${runId}`),
  createTestRun: (projectId: string, body: import('./types').CreateTestRunForm) => post<import('./types').TestRun>(`/projects/${projectId}/test-runs`, body),
  deleteTestRun: (projectId: string, runId: string) => del(`/projects/${projectId}/test-runs/${runId}`),
  completeTestRun: (projectId: string, runId: string) => post<import('./types').TestRun>(`/projects/${projectId}/test-runs/${runId}/complete`, {}),
  runExecutions: (projectId: string, runId: string) => get<import('./types').TestCaseExecution[]>(`/projects/${projectId}/test-runs/${runId}/executions`),
  updateExecution: (projectId: string, runId: string, execId: string, body: { status: string; actualResult?: string; notes?: string; executedBy?: string }) =>
    put<import('./types').TestCaseExecution>(`/projects/${projectId}/test-runs/${runId}/executions/${execId}`, body),

  // Flaky Tests
  recomputeFlakiness: (projectId: string) =>
    post<{ recomputed: number; projectId: string }>(`/projects/${projectId}/flakiness/recompute`, {}),
  triggerFlakyFix: (projectId: string, body: { testId: string; flakyId: string; githubConfigId: string }) =>
    post<import('./types').FlakyFixResult>(`/projects/${projectId}/flakiness/fix`, body),
}
