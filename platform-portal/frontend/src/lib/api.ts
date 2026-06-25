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

async function patch(path: string): Promise<void> {
  const res = await fetch(BASE + path, { method: 'PATCH' })
  if (!res.ok) throw new Error(`PATCH ${path} → ${res.status}`)
}

async function patchJson<T>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { method: 'PATCH', headers: { Accept: 'application/json' } })
  if (!res.ok) throw new Error(`PATCH ${path} → ${res.status}`)
  return res.json() as Promise<T>
}

/** PUT carrying the X-Actor header; surfaces the server's error message body. */
async function putActor<T>(path: string, body: unknown, actor: string): Promise<T> {
  const res = await fetch(BASE + path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-Actor': actor },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    let msg = `PUT ${path} → ${res.status}`
    try {
      const e = (await res.json()) as { message?: string }
      if (e?.message) msg = e.message
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }
  return res.json() as Promise<T>
}

export const api = {
  overview: (days = 7) =>
    get<{ summary: import('./types').OrgSummary; recentAlerts: import('./types').Alert[] }>(
      `/overview?days=${days}`,
    ),
  organizations: () => get<import('./types').Organization[]>('/organizations'),
  projects: (orgSlug?: string) =>
    get<import('./types').Project[]>(orgSlug ? `/projects?orgSlug=${orgSlug}` : '/projects'),
  projectDetail: (id: string, days = 7) =>
    get<import('./types').ProjectDetail>(`/projects/${id}?days=${days}`),
  executions: (projectId: string, limit = 20) =>
    get<import('./types').ExecutionSummary[]>(`/projects/${projectId}/executions?limit=${limit}`),
  passRateTrend: (projectId: string, days = 30) =>
    get<import('./types').PassRatePoint[]>(`/projects/${projectId}/trends/pass-rate?days=${days}`),
  flakiness: (projectId: string, limit = 20, classification?: string) => {
    let u = `/projects/${projectId}/flakiness?limit=${limit}`
    if (classification) u += `&classification=${classification}`
    return get<import('./types').FlakinessItem[]>(u)
  },
  qualityGate: (projectId: string) =>
    get<import('./types').QualityGateResult>(`/projects/${projectId}/quality-gate`),
  analyses: (projectId: string, days = 7) =>
    get<import('./types').FailureAnalysis[]>(`/projects/${projectId}/analyses?days=${days}`),
  runDetail: (runId: string) =>
    get<{ summary: import('./types').ExecutionSummary; testCases: import('./types').TestCase[] }>(
      `/executions/${runId}`,
    ),
  alerts: (days = 7) => get<import('./types').Alert[]>(`/alerts?days=${days}`),
  apiKeys: () => get<import('./types').ApiKey[]>('/api-keys'),
  createApiKey: (body: { name: string; ttlDays?: number }) =>
    post<{ id: string; prefix: string; rawKey: string; expiresAt: string | null }>(
      '/api-keys',
      body,
    ),
  revokeApiKey: (id: string) => del(`/api-keys/${id}`),
  impactSummary: (projectId: string) =>
    get<import('./types').CoverageSummary>(`/projects/${projectId}/impact/summary`),
  testImpact: (projectId: string, changedFiles: string[]) => {
    const params = changedFiles.map(f => `changedFiles=${encodeURIComponent(f)}`).join('&')
    return get<import('./types').TestImpactResult>(`/projects/${projectId}/impact?${params}`)
  },
  aiSettings: () => get<import('./types').AiSettings>('/ai/settings'),
  updateAiSettings: (body: import('./types').AiSettingsUpdate) =>
    put<import('./types').AiSettings>('/ai/settings', body),
  testAiConnection: (body: { provider?: string; model?: string; apiKey?: string }) =>
    post<import('./types').TestConnectionResult>('/ai/settings/test', body),
  scopedAiEffective: (projectId: string) =>
    get<Record<string, string | null>>(`/ai/settings/scoped/effective?projectId=${projectId}`),
  setScopedAi: (scope: string, scopeId: string, key: string, value: string) =>
    put<{ status: string }>(`/ai/settings/scoped/${scope}/${scopeId}`, { key, value }),
  analyseResult: (projectId: string, resultId: string) =>
    post<import('./types').FailureAnalysis>(
      `/ai/projects/${projectId}/results/${resultId}/analyse`,
      {},
    ),
  analyseNow: (hours = 24) =>
    post<import('./types').AnalyseNowResult>(`/ai/analyse/run-now?hours=${hours}`, {}),
  requirements: (
    projectId: string,
    params?: {
      status?: string
      issueType?: string
      search?: string
      area?: string
      team?: string
      iteration?: string
    },
  ) => {
    const q = new URLSearchParams()
    if (params?.status) q.set('status', params.status)
    if (params?.issueType) q.set('issueType', params.issueType)
    if (params?.search) q.set('search', params.search)
    if (params?.area) q.set('area', params.area)
    if (params?.team) q.set('team', params.team)
    if (params?.iteration) q.set('iteration', params.iteration)
    const qs = q.toString()
    return get<import('./types').Requirement[]>(
      `/projects/${projectId}/requirements${qs ? '?' + qs : ''}`,
    )
  },
  requirementsPage: (
    projectId: string,
    params: {
      page?: number
      size?: number
      status?: string
      issueType?: string
      search?: string
      area?: string
      team?: string
      iteration?: string
    },
  ) => {
    const q = new URLSearchParams()
    q.set('page', String(params.page ?? 0))
    q.set('size', String(params.size ?? 50))
    if (params.status) q.set('status', params.status)
    if (params.issueType) q.set('issueType', params.issueType)
    if (params.search) q.set('search', params.search)
    if (params.area) q.set('area', params.area)
    if (params.team) q.set('team', params.team)
    if (params.iteration) q.set('iteration', params.iteration)
    return get<import('./types').PagedRequirements>(
      `/projects/${projectId}/requirements/page?${q.toString()}`,
    )
  },
  requirementStats: (projectId: string) =>
    get<import('./types').RequirementStats>(`/projects/${projectId}/requirements/stats`),
  requirementDetail: (projectId: string, reqId: string) =>
    get<import('./types').Requirement>(`/projects/${projectId}/requirements/${reqId}`),
  requirementRelations: (projectId: string, reqId: string) =>
    get<import('./types').RequirementRelations>(
      `/projects/${projectId}/requirements/${reqId}/relations`,
    ),

  // ADO org structure
  adoSummary: (projectId: string) =>
    get<import('./types').AdoStructureSummary>(`/projects/${projectId}/ado/summary`),
  adoTeams: (projectId: string) =>
    get<import('./types').AdoTeam[]>(`/projects/${projectId}/ado/teams`),
  adoAreas: (projectId: string) =>
    get<import('./types').AdoArea[]>(`/projects/${projectId}/ado/areas`),
  adoIterations: (projectId: string) =>
    get<import('./types').AdoIteration[]>(`/projects/${projectId}/ado/iterations`),
  adoUsers: (projectId: string) =>
    get<import('./types').AdoUser[]>(`/projects/${projectId}/ado/users`),
  syncAdoStructure: (projectId: string) =>
    post<{
      success: boolean
      teams?: number
      areas?: number
      iterations?: number
      users?: number
      error?: string
    }>(`/projects/${projectId}/ado/sync-structure`, {}),
  setUserQualityRole: (projectId: string, userId: string, qualityRole: string | null) =>
    put<import('./types').AdoUser>(`/projects/${projectId}/ado/users/${userId}/quality-role`, {
      qualityRole,
    }),

  // Quality dashboards
  qualityOverview: (projectId: string) =>
    get<import('./types').QualityOverview>(`/projects/${projectId}/quality/overview`),
  qualityEngineers: (projectId: string) =>
    get<import('./types').EngineerStat[]>(`/projects/${projectId}/quality/engineers`),
  qualityWorkItems: (
    projectId: string,
    params: { person: string; attribution?: string; type?: string; status?: string },
  ) => {
    const q = new URLSearchParams({ person: params.person })
    if (params.attribution) q.set('attribution', params.attribution)
    if (params.type) q.set('type', params.type)
    if (params.status) q.set('status', params.status)
    return get<import('./types').QualityWorkItem[]>(
      `/projects/${projectId}/quality/work-items?${q.toString()}`,
    )
  },
  qualityInvolvementItems: (projectId: string, person: string, kind: string) =>
    get<import('./types').QualityWorkItem[]>(
      `/projects/${projectId}/quality/involvement-items?person=${encodeURIComponent(person)}&kind=${kind}`,
    ),
  qualityActivity: (projectId: string, person: string, limit = 50) =>
    get<import('./types').ActivityEvent[]>(
      `/projects/${projectId}/quality/activity?person=${encodeURIComponent(person)}&limit=${limit}`,
    ),
  syncQualityHistory: (projectId: string) =>
    post<{
      success: boolean
      started?: boolean
      status?: string
      message?: string
      error?: string
    }>(`/projects/${projectId}/quality/sync-history`, {}),
  qualityHistoryStatus: (projectId: string) =>
    get<{ running: boolean }>(`/projects/${projectId}/quality/history-status`),

  // Productivity / cycle time
  productivityByArea: (projectId: string) =>
    get<import('./types').ProductivityOverview>(`/projects/${projectId}/productivity/by-area`),
  productivityWipItems: (projectId: string, area?: string, over = true) => {
    const q = new URLSearchParams({ over: String(over) })
    if (area) q.set('area', area)
    return get<import('./types').OverThresholdItem[]>(
      `/projects/${projectId}/productivity/wip-items?${q.toString()}`,
    )
  },
  productivityLeadByArea: (projectId: string) =>
    get<import('./types').LeadOverview>(`/projects/${projectId}/productivity/lead-by-area`),
  productivityLeadItems: (projectId: string, area?: string) =>
    get<import('./types').LeadItem[]>(
      `/projects/${projectId}/productivity/lead-items${area ? `?area=${encodeURIComponent(area)}` : ''}`,
    ),
  setProductivityThreshold: (projectId: string, thresholdHours: number) =>
    put<{ thresholdHours: number }>(`/projects/${projectId}/productivity/threshold`, {
      thresholdHours,
    }),
  prAnalyses: (projectId: string, limit = 30) =>
    get<import('./types').PrAnalysis[]>(`/projects/${projectId}/pr-analyses?limit=${limit}`),
  impactAnalyses: (projectId: string) =>
    get<import('./types').ImpactAnalysis[]>(`/projects/${projectId}/impact-analyses`),
  impactAnalysis: (projectId: string, id: string) =>
    get<import('./types').ImpactAnalysis>(`/projects/${projectId}/impact-analyses/${id}`),
  codebasePrs: (projectId: string) =>
    get<import('./types').CodabasePr[]>(`/projects/${projectId}/impact-analyses/prs`),
  createImpactAnalysis: (projectId: string, body: import('./types').CreateImpactAnalysisForm) =>
    post<import('./types').ImpactAnalysis>(`/projects/${projectId}/impact-analyses`, body),
  // Organizations (top tenant — ADO-first)
  createOrganization: (body: import('./types').CreateOrganizationForm) =>
    post<import('./types').Organization>('/organizations', body),
  updateOrganization: (id: string, body: import('./types').UpdateOrganizationForm) =>
    put<import('./types').Organization>(`/organizations/${id}`, body),
  uploadOrgLogo: (id: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return fetch(`${BASE}/organizations/${id}/logo`, { method: 'POST', body: form }).then(r => {
      if (!r.ok) throw new Error('Upload failed')
      return r.json() as Promise<import('./types').Organization>
    })
  },
  deleteOrganization: (id: string) => del(`/organizations/${id}`),
  // Teams (sub-entities of a project — ADO-first)
  teams: (projectId: string) => get<import('./types').Team[]>(`/projects/${projectId}/teams`),
  createTeam: (projectId: string, body: import('./types').CreateTeamForm) =>
    post<import('./types').Team>(`/projects/${projectId}/teams`, body),
  updateTeam: (projectId: string, id: string, body: import('./types').UpdateTeamForm) =>
    put<import('./types').Team>(`/projects/${projectId}/teams/${id}`, body),
  deleteTeam: (projectId: string, id: string) => del(`/projects/${projectId}/teams/${id}`),
  createProject: (body: import('./types').CreateProjectForm) =>
    post<import('./types').Project>('/projects', body),
  updateProject: (id: string, body: import('./types').UpdateProjectForm) =>
    put<import('./types').Project>(`/projects/${id}`, body),
  deleteProject: (id: string) => del(`/projects/${id}`),
  integrations: (projectId: string) =>
    get<import('./types').IntegrationConfig[]>(`/projects/${projectId}/integrations`),
  inheritedIntegrations: (projectId: string) =>
    get<import('./types').InheritedCredential[]>(`/projects/${projectId}/integrations/inherited`),
  saveIntegration: (projectId: string, body: import('./types').SaveIntegrationConfigForm) =>
    post<import('./types').IntegrationConfig>(`/projects/${projectId}/integrations`, body),
  deleteIntegration: (projectId: string, configId: string) =>
    del(`/projects/${projectId}/integrations/${configId}`),
  syncIntegrations: (projectId: string) =>
    post<Record<string, unknown>>(`/projects/${projectId}/integrations/sync`, {}),

  // Test Suites
  testSuites: (projectId: string) =>
    get<import('./types').TestSuite[]>(`/projects/${projectId}/test-suites`),
  suiteCases: (projectId: string, suiteId: string) =>
    get<import('./types').SelectableTestCase[]>(
      `/projects/${projectId}/test-suites/${suiteId}/cases`,
    ),
  caseSuites: (projectId: string, tcId: string) =>
    get<string[]>(`/projects/${projectId}/test-cases/${tcId}/suites`),
  setCaseSuites: (projectId: string, tcId: string, suiteIds: string[]) =>
    put<unknown>(`/projects/${projectId}/test-cases/${tcId}/suites`, { suiteIds }),
  replaceSuiteMembers: (projectId: string, suiteId: string, testCaseIds: string[]) =>
    put<unknown>(`/projects/${projectId}/test-suites/${suiteId}/members`, { testCaseIds }),
  createTestSuite: (projectId: string, body: import('./types').CreateTestSuiteForm) =>
    post<import('./types').TestSuite>(`/projects/${projectId}/test-suites`, body),
  updateTestSuite: (
    projectId: string,
    suiteId: string,
    body: import('./types').CreateTestSuiteForm,
  ) => put<import('./types').TestSuite>(`/projects/${projectId}/test-suites/${suiteId}`, body),
  deleteTestSuite: (projectId: string, suiteId: string) =>
    del(`/projects/${projectId}/test-suites/${suiteId}`),

  // Test-case tags (Kiwi-style)
  caseTags: (projectId: string, tcId: string) =>
    get<string[]>(`/projects/${projectId}/test-cases/${tcId}/tags`),
  addCaseTag: (projectId: string, tcId: string, name: string) =>
    post<string[]>(`/projects/${projectId}/test-cases/${tcId}/tags`, { name }),
  removeCaseTag: (projectId: string, tcId: string, name: string) =>
    del(`/projects/${projectId}/test-cases/${tcId}/tags/${encodeURIComponent(name)}`),
  tagSuggestions: (projectId: string) => get<string[]>(`/projects/${projectId}/tags`),

  // Mapping rules (config-driven Mapping Suggester): default → org → project
  mappingRulesDefault: () => get<import('./types').MappingRulesetView>('/mapping-rules/default'),
  orgMappingRules: (orgId: string) =>
    get<import('./types').MappingRulesetView>(`/organizations/${orgId}/mapping-rules`),
  saveOrgMappingRules: (orgId: string, json: string, actor: string) =>
    putActor<import('./types').MappingRulesetView>(
      `/organizations/${orgId}/mapping-rules`,
      { json },
      actor,
    ),
  resetOrgMappingRules: (orgId: string) => del(`/organizations/${orgId}/mapping-rules`),
  projectMappingRules: (projectId: string) =>
    get<import('./types').MappingRulesetView>(`/projects/${projectId}/mapping-rules`),
  saveProjectMappingRules: (projectId: string, json: string, actor: string) =>
    putActor<import('./types').MappingRulesetView>(
      `/projects/${projectId}/mapping-rules`,
      { json },
      actor,
    ),
  resetProjectMappingRules: (projectId: string) => del(`/projects/${projectId}/mapping-rules`),

  // ADO schema discovery + mapping suggestion
  adoProjects: (projectId: string) =>
    get<import('./types').AdoProject[]>(`/projects/${projectId}/ado/projects`),
  adoTypes: (projectId: string, adoProject: string) =>
    get<import('./types').AdoTypeSummary[]>(
      `/projects/${projectId}/ado/work-item-types?adoProject=${encodeURIComponent(adoProject)}`,
    ),
  adoTypeSchema: (projectId: string, adoProject: string, type: string) =>
    get<import('./types').AdoTypeSchema>(
      `/projects/${projectId}/ado/work-item-types/${encodeURIComponent(type)}/schema?adoProject=${encodeURIComponent(adoProject)}`,
    ),
  schemaDrift: (projectId: string, adoProject: string, type: string) =>
    get<import('./types').SchemaDriftReport>(
      `/projects/${projectId}/ado/work-item-types/${encodeURIComponent(type)}/drift?adoProject=${encodeURIComponent(adoProject)}`,
    ),
  captureSchemaBaseline: (projectId: string, adoProject: string, type: string) =>
    post<import('./types').SchemaDriftReport>(
      `/projects/${projectId}/ado/work-item-types/${encodeURIComponent(type)}/drift/baseline?adoProject=${encodeURIComponent(adoProject)}`,
      {},
    ),

  // Requirements coverage matrix
  coverage: (projectId: string, params?: { area?: string; team?: string; iteration?: string }) => {
    const q = new URLSearchParams()
    if (params?.area) q.set('area', params.area)
    if (params?.team) q.set('team', params.team)
    if (params?.iteration) q.set('iteration', params.iteration)
    const qs = q.toString()
    return get<import('./types').CoverageReport>(
      `/projects/${projectId}/coverage${qs ? '?' + qs : ''}`,
    )
  },

  // RBAC role administration (X-Actor identifies the acting user)
  rbacMembers: (scope: string, scopeId?: string) =>
    get<import('./types').TeamMemberAssignment[]>(
      `/rbac/members?scope=${scope}${scopeId ? `&scopeId=${scopeId}` : ''}`,
    ),
  grantRole: async (body: import('./types').GrantRoleForm, actor: string) => {
    const res = await fetch(`${BASE}/rbac/members`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Actor': actor },
      body: JSON.stringify(body),
    })
    if (!res.ok) throw new Error(`POST /rbac/members → ${res.status}`)
    return res.json() as Promise<import('./types').TeamMemberAssignment>
  },
  revokeRole: async (id: string, actor: string) => {
    const res = await fetch(`${BASE}/rbac/members/${id}`, {
      method: 'DELETE',
      headers: { 'X-Actor': actor },
    })
    if (!res.ok) throw new Error(`DELETE /rbac/members/${id} → ${res.status}`)
  },

  // Environments (V50)
  environments: (projectId: string) =>
    get<import('./types').Environment[]>(`/projects/${projectId}/environments`),
  createEnvironment: (
    projectId: string,
    body: { name: string; description?: string; properties?: Record<string, string> },
  ) => post<import('./types').Environment>(`/projects/${projectId}/environments`, body),
  deleteEnvironment: (projectId: string, envId: string) =>
    del(`/projects/${projectId}/environments/${envId}`),

  // Test-case parametrization properties (V49)
  caseProperties: (projectId: string, tcId: string) =>
    get<import('./types').CaseProperty[]>(`/projects/${projectId}/test-cases/${tcId}/properties`),
  replaceCaseProperties: (
    projectId: string,
    tcId: string,
    properties: import('./types').CaseProperty[],
  ) =>
    put<import('./types').CaseProperty[]>(
      `/projects/${projectId}/test-cases/${tcId}/properties`,
      properties,
    ),

  // Test Cases
  testCases: (
    projectId: string,
    params?: {
      status?: string
      suiteId?: string
      search?: string
      area?: string
      teamId?: string
      iteration?: string
    },
  ) => {
    const q = new URLSearchParams()
    if (params?.status) q.set('status', params.status)
    if (params?.suiteId) q.set('suiteId', params.suiteId)
    if (params?.search) q.set('search', params.search)
    if (params?.area) q.set('area', params.area)
    if (params?.teamId) q.set('teamId', params.teamId)
    if (params?.iteration) q.set('iteration', params.iteration)
    const qs = q.toString()
    return get<import('./types').ManagedTestCase[]>(
      `/projects/${projectId}/test-cases${qs ? '?' + qs : ''}`,
    )
  },
  selectableTestCases: (
    projectId: string,
    params?: { status?: string; area?: string; iteration?: string; teamId?: string; q?: string },
  ) => {
    const sp = new URLSearchParams()
    if (params?.status) sp.set('status', params.status)
    if (params?.area) sp.set('area', params.area)
    if (params?.iteration) sp.set('iteration', params.iteration)
    if (params?.teamId) sp.set('teamId', params.teamId)
    if (params?.q) sp.set('q', params.q)
    const qs = sp.toString()
    return get<import('./types').SelectableTestCase[]>(
      `/projects/${projectId}/test-cases/selectable${qs ? '?' + qs : ''}`,
    )
  },
  testCase: (projectId: string, tcId: string) =>
    get<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}`),
  createTestCase: (projectId: string, body: import('./types').CreateTestCaseForm) =>
    post<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases`, body),
  updateTestCase: (projectId: string, tcId: string, body: import('./types').CreateTestCaseForm) =>
    put<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}`, body),
  deleteTestCase: (projectId: string, tcId: string) =>
    del(`/projects/${projectId}/test-cases/${tcId}`),
  replaceSteps: (
    projectId: string,
    tcId: string,
    steps: { action: string; expectedResult?: string; notes?: string }[],
  ) =>
    put<import('./types').ManagedTestCase>(
      `/projects/${projectId}/test-cases/${tcId}/steps`,
      steps,
    ),
  submitForReview: (projectId: string, tcId: string) =>
    post<import('./types').ManagedTestCase>(
      `/projects/${projectId}/test-cases/${tcId}/submit-review`,
      {},
    ),
  approveTestCase: (projectId: string, tcId: string) =>
    post<import('./types').ManagedTestCase>(
      `/projects/${projectId}/test-cases/${tcId}/approve`,
      {},
    ),
  rejectTestCase: (projectId: string, tcId: string) =>
    post<import('./types').ManagedTestCase>(`/projects/${projectId}/test-cases/${tcId}/reject`, {}),
  triggerAutomation: (projectId: string, tcId: string, githubConfigId?: string) => {
    const q = githubConfigId ? `?githubConfigId=${githubConfigId}` : ''
    return post<import('./types').ManagedTestCase>(
      `/projects/${projectId}/test-cases/${tcId}/generate-automation${q}`,
      {},
    )
  },
  generateTestCasesFromAI: (projectId: string, requirementIds?: string[]) =>
    post<{ workflowId: string; message: string }>(`/projects/${projectId}/test-cases/generate`, {
      requirementIds,
    }),
  linkRequirement: (projectId: string, tcId: string, requirementId: string) =>
    post<import('./types').ManagedTestCase>(
      `/projects/${projectId}/test-cases/${tcId}/link-requirement/${requirementId}`,
      {},
    ),
  unlinkRequirement: (projectId: string, tcId: string, requirementId: string) =>
    del(`/projects/${projectId}/test-cases/${tcId}/link-requirement/${requirementId}`),
  applyImpactSuggestion: (
    projectId: string,
    tcId: string,
    body: import('./types').ApplySuggestionForm,
  ) =>
    post<import('./types').ManagedTestCase>(
      `/projects/${projectId}/test-cases/${tcId}/apply-suggestion`,
      body,
    ),

  // Test Runs
  testRuns: (projectId: string) =>
    get<import('./types').TestRun[]>(`/projects/${projectId}/test-runs`),
  testRun: (projectId: string, runId: string) =>
    get<import('./types').TestRun>(`/projects/${projectId}/test-runs/${runId}`),
  createTestRun: (projectId: string, body: import('./types').CreateTestRunForm) =>
    post<import('./types').TestRun>(`/projects/${projectId}/test-runs`, body),
  deleteTestRun: (projectId: string, runId: string) =>
    del(`/projects/${projectId}/test-runs/${runId}`),
  completeTestRun: (projectId: string, runId: string) =>
    post<import('./types').TestRun>(`/projects/${projectId}/test-runs/${runId}/complete`, {}),
  runExecutions: (projectId: string, runId: string) =>
    get<import('./types').TestCaseExecution[]>(
      `/projects/${projectId}/test-runs/${runId}/executions`,
    ),
  updateExecution: (
    projectId: string,
    runId: string,
    execId: string,
    body: { status: string; actualResult?: string; notes?: string; executedBy?: string },
  ) =>
    put<import('./types').TestCaseExecution>(
      `/projects/${projectId}/test-runs/${runId}/executions/${execId}`,
      body,
    ),

  // Releases
  releases: (projectId: string) =>
    get<import('./types').Release[]>(`/projects/${projectId}/releases`),
  createRelease: (projectId: string, body: import('./types').CreateReleaseForm) =>
    post<import('./types').Release>(`/projects/${projectId}/releases`, body),
  updateRelease: (projectId: string, id: string, body: import('./types').CreateReleaseForm) =>
    put<import('./types').Release>(`/projects/${projectId}/releases/${id}`, body),
  deleteRelease: (projectId: string, id: string) => del(`/projects/${projectId}/releases/${id}`),

  // Unified Test Execution list — manual runs + automated executions merged
  unifiedExecutions: (
    projectId: string,
    params?: {
      type?: 'ALL' | 'MANUAL' | 'AUTOMATED'
      teamId?: string
      area?: string
      iteration?: string
      limit?: number
    },
  ) => {
    const q = new URLSearchParams()
    q.set('type', params?.type ?? 'ALL')
    q.set('limit', String(params?.limit ?? 100))
    if (params?.teamId) q.set('teamId', params.teamId)
    if (params?.area) q.set('area', params.area)
    if (params?.iteration) q.set('iteration', params.iteration)
    return get<import('./types').UnifiedExecutionItem[]>(
      `/projects/${projectId}/test-execution/unified?${q.toString()}`,
    )
  },

  updateExecutionScope: (
    projectId: string,
    runId: string,
    params: { iterationPath?: string; areaSlug?: string },
  ) => {
    const q = new URLSearchParams()
    if (params.iterationPath) q.set('iterationPath', params.iterationPath)
    if (params.areaSlug) q.set('areaSlug', params.areaSlug)
    return patch(
      `/projects/${projectId}/executions/${encodeURIComponent(runId)}/scope?${q.toString()}`,
    )
  },

  // Test Execution — release board (grouped by team, with coverage)
  testExecutionBoard: (
    projectId: string,
    scope?: { iteration?: string; area?: string; team?: string },
  ) => {
    const q = new URLSearchParams()
    if (scope?.iteration) q.set('iteration', scope.iteration)
    if (scope?.area) q.set('area', scope.area)
    if (scope?.team) q.set('team', scope.team)
    const qs = q.toString()
    return get<import('./types').ReleaseBoard>(
      `/projects/${projectId}/test-execution-board${qs ? '?' + qs : ''}`,
    )
  },
  // Test Execution monitor (rollups by Release / Sprint / Area / Team)
  testExecutionBy: (
    projectId: string,
    dimension: 'release' | 'sprint' | 'area' | 'team',
    scope?: { area?: string; team?: string; iteration?: string },
  ) => {
    const q = new URLSearchParams()
    if (scope?.area) q.set('area', scope.area)
    if (scope?.team) q.set('team', scope.team)
    if (scope?.iteration) q.set('iteration', scope.iteration)
    const qs = q.toString()
    return get<import('./types').ExecMonitorOverview>(
      `/projects/${projectId}/test-execution/by-${dimension}${qs ? '?' + qs : ''}`,
    )
  },
  testExecutionRuns: (
    projectId: string,
    dimension: 'release' | 'sprint' | 'area' | 'team',
    value?: string | null,
  ) => {
    const q = new URLSearchParams({ dimension })
    if (value) q.set('value', value)
    return get<import('./types').ExecRunSummary[]>(
      `/projects/${projectId}/test-execution-runs?${q.toString()}`,
    )
  },

  // Review Queue / Work Items
  workItems: (projectId: string) =>
    get<import('./types').WorkItem[]>(`/projects/${projectId}/work-items`),
  approveReviewRequest: (projectId: string, requestId: string, decidedBy: string) =>
    post<unknown>(`/projects/${projectId}/work-items/review-requests/${requestId}/approve`, {
      decidedBy,
    }),
  rejectReviewRequest: (projectId: string, requestId: string, decidedBy: string) =>
    post<unknown>(`/projects/${projectId}/work-items/review-requests/${requestId}/reject`, {
      decidedBy,
    }),

  // Flaky Tests
  recomputeFlakiness: (projectId: string) =>
    post<{ recomputed: number; projectId: string }>(
      `/projects/${projectId}/flakiness/recompute`,
      {},
    ),
  triggerFlakyFix: (
    projectId: string,
    body: { testId: string; flakyId: string; githubConfigId: string },
  ) => post<import('./types').FlakyFixResult>(`/projects/${projectId}/flakiness/fix`, body),

  // Automated Test Catalog
  automatedTests: (
    projectId: string,
    params?: {
      days?: number
      search?: string
      status?: string
      tags?: string[]
      browsers?: string[]
      annotationTypes?: string[]
      labelKey?: string
      labelValue?: string
      specFile?: string
    },
  ) => {
    const q = new URLSearchParams()
    q.set('days', String(params?.days ?? 30))
    q.set('status', params?.status ?? 'ALL')
    if (params?.search) q.set('search', params.search)
    if (params?.labelKey) q.set('labelKey', params.labelKey)
    if (params?.labelValue) q.set('labelValue', params.labelValue)
    if (params?.specFile) q.set('specFile', params.specFile)
    params?.tags?.forEach(t => q.append('tags', t))
    params?.browsers?.forEach(b => q.append('browsers', b))
    params?.annotationTypes?.forEach(a => q.append('annotationTypes', a))
    return get<import('./types').AutomatedTestSummary[]>(
      `/projects/${projectId}/automated-tests?${q.toString()}`,
    )
  },
  automatedTestDetail: (projectId: string, testId: string, days = 30) =>
    get<import('./types').AutomatedTestDetail>(
      `/projects/${projectId}/automated-tests/detail?testId=${encodeURIComponent(testId)}&days=${days}`,
    ),
  automatedTestTags: (projectId: string, days = 30) =>
    get<string[]>(`/projects/${projectId}/automated-tests/tags?days=${days}`),
  automatedTestBrowsers: (projectId: string, days = 30) =>
    get<string[]>(`/projects/${projectId}/automated-tests/browsers?days=${days}`),
  automatedTestAnnotationTypes: (projectId: string, days = 30) =>
    get<string[]>(`/projects/${projectId}/automated-tests/annotation-types?days=${days}`),
  automatedTestLabelKeys: (projectId: string, days = 30) =>
    get<string[]>(`/projects/${projectId}/automated-tests/label-keys?days=${days}`),
  automatedTestLabelValues: (projectId: string, days = 30, labelKey: string) =>
    get<string[]>(
      `/projects/${projectId}/automated-tests/label-values?days=${days}&labelKey=${encodeURIComponent(labelKey)}`,
    ),
  /** Returns the absolute URL to download a trace ZIP (used to open the Playwright trace viewer). */
  traceUrl: (resultId: string) => `${window.location.origin}/api/portal/traces/${resultId}`,

  // Integration Credentials (Org→Team→Project cascade / Admin PAT)
  credentials: (scope: string, scopeId?: string) => {
    const q = scopeId ? `?scope=${scope}&scopeId=${scopeId}` : `?scope=${scope}`
    return get<import('./types').Credential[]>(`/credentials${q}`)
  },
  saveCredential: (body: import('./types').SaveCredentialForm) =>
    post<import('./types').Credential>('/credentials', body),
  deleteCredential: (id: string) => del(`/credentials/${id}`),
  testCredential: (id: string) =>
    post<{ ok: boolean; message: string }>(`/credentials/${id}/test`, {}),
  githubRepos: (credId: string) =>
    get<import('./types').GithubRepo[]>(`/credentials/${credId}/github/repos`),
  setGithubRepos: (credId: string, repos: import('./types').GithubRepo[]) =>
    put<import('./types').GithubRepo[]>(`/credentials/${credId}/github/repos`, { repos }),

  // GitHub repo cache (avoids live API calls for large orgs)
  syncGitHubRepos: (credId: string) =>
    post<import('./types').GitHubCacheResult>(`/credentials/${credId}/github/repos/sync`, {}),
  cachedGitHubRepos: (credId: string) =>
    get<import('./types').GitHubCacheResult>(`/credentials/${credId}/github/repos/cached`),
  updateSyncInterval: (credId: string, minutes: number) =>
    patchJson<import('./types').Credential>(
      `/credentials/${credId}/sync-interval?minutes=${minutes}`,
    ),

  // Project-level GitHub repo assignments
  projectGitHubRepos: (projectId: string) =>
    get<import('./types').ProjectRepoAssignment[]>(`/projects/${projectId}/github/repos`),
  setProjectGitHubRepos: (
    projectId: string,
    assignments: { repoFullName: string; role: string; credentialId: string }[],
  ) =>
    put<import('./types').ProjectRepoAssignment[]>(`/projects/${projectId}/github/repos`, {
      assignments,
    }),

  // GitHub Actions workflows (TEST_AUTOMATION repos)
  githubWorkflows: (projectId: string) =>
    get<import('./types').GitHubWorkflow[]>(`/projects/${projectId}/github/workflows`),
  githubWorkflowRuns: (projectId: string, repo: string, workflowId: number, limit = 15) =>
    get<import('./types').GitHubWorkflowRun[]>(
      `/projects/${projectId}/github/workflow-runs?repo=${encodeURIComponent(repo)}&workflowId=${workflowId}&limit=${limit}`,
    ),
  triggerWorkflow: (
    projectId: string,
    body: {
      repoFullName: string
      workflowId: number
      ref: string
      inputs?: Record<string, string>
    },
  ) =>
    post<import('./types').WorkflowDispatchResult>(
      `/projects/${projectId}/github/workflow-dispatch`,
      body,
    ),
}
