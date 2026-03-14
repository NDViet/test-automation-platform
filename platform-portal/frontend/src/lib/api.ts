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
}
