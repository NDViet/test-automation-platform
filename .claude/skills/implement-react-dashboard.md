# Skill: implement-react-dashboard

Implement a React dashboard component in `platform-portal/frontend` using React 19, shadcn/ui, TanStack Query, and Recharts.

## Context

- React 19.2.4, Vite 7.3.1, TypeScript
- UI: shadcn/ui components + Tailwind CSS
- Charts: Recharts 2.x
- State: Zustand 4.x
- Data fetching: TanStack Query 5.x (server state)
- WebSocket: SockJS + STOMP (live execution updates)
- Auth: React OAuth2 — `useAuth()` hook provides `teamId`, `role`
- BFF API base: `/bff/`

## Dashboard Pages

| Page | Route | Primary User |
|---|---|---|
| OrgDashboard | `/` | Architect |
| TeamDashboard | `/teams/:teamId` | Team Lead |
| TestDetail | `/tests/:testId` | Developer |
| ExecutionDetail | `/executions/:runId` | Developer |

## Instructions

### 1. Read existing components first
Read all files in `platform-portal/frontend/src/components/` and `src/pages/` before creating new ones to align with existing patterns.

### 2. Create the API client hook
```typescript
// hooks/useOrgDashboard.ts
export function useOrgDashboard() {
  return useQuery({
    queryKey: ['org', 'dashboard'],
    queryFn: () => apiFetch<OrgDashboardData>('/bff/org/dashboard'),
    refetchInterval: 60_000,    // refresh every 60s
    staleTime: 30_000,
  });
}

export function useTeamDashboard(teamId: string) {
  return useQuery({
    queryKey: ['team', teamId, 'dashboard'],
    queryFn: () => apiFetch<TeamDashboardData>(`/bff/teams/${teamId}/dashboard`),
    enabled: !!teamId,
    refetchInterval: 30_000,
  });
}
```

### 3. Implement `QualityHealthMatrix` (org dashboard, architect view)
```tsx
// components/QualityHealthMatrix.tsx
interface TeamQualityRow {
  teamId: string;
  teamName: string;
  passRate: number;          // 0.0–1.0
  flakyCount: number;
  brokenCount: number;
  trend: number;             // delta vs last week, e.g. +0.021
  gate: 'PASS' | 'WARN' | 'FAIL';
}

export function QualityHealthMatrix({ teams }: { teams: TeamQualityRow[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Team</TableHead>
          <TableHead>Pass Rate</TableHead>
          <TableHead>Flaky Tests</TableHead>
          <TableHead>Broken</TableHead>
          <TableHead>Trend</TableHead>
          <TableHead>Gate</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {teams.map(team => (
          <TableRow key={team.teamId}
            className={team.gate === 'FAIL' ? 'bg-red-50' : ''}>
            <TableCell>
              <Link to={`/teams/${team.teamId}`}>{team.teamName}</Link>
            </TableCell>
            <TableCell>
              <PassRateBadge rate={team.passRate} />
            </TableCell>
            <TableCell>{team.flakyCount}</TableCell>
            <TableCell>{team.brokenCount}</TableCell>
            <TableCell>
              <TrendIndicator delta={team.trend} />
            </TableCell>
            <TableCell>
              <GateBadge gate={team.gate} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

### 4. Implement `PassRateTrendChart` (Recharts)
```tsx
// components/PassRateTrendChart.tsx
interface DataPoint {
  date: string;   // ISO date string
  passRate: number;
  total: number;
}

export function PassRateTrendChart({ data, teamName }: {
  data: DataPoint[];
  teamName: string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Pass Rate — {teamName}</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={250}>
          <AreaChart data={data}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date"
              tickFormatter={d => format(parseISO(d), 'MMM d')} />
            <YAxis
              domain={[0, 1]}
              tickFormatter={v => `${(v * 100).toFixed(0)}%`} />
            <Tooltip
              formatter={(v: number) => `${(v * 100).toFixed(1)}%`} />
            <ReferenceLine y={0.9} stroke="#ef4444" strokeDasharray="4 4"
              label="SLA 90%" />
            <Area type="monotone" dataKey="passRate"
              stroke="#3b82f6" fill="#bfdbfe"
              strokeWidth={2} />
          </AreaChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
```

### 5. Implement `AiAnalysisCard` (test detail view)
```tsx
// components/AiAnalysisCard.tsx
export function AiAnalysisCard({ analysis }: { analysis: FailureAnalysis | null }) {
  if (!analysis) return null;

  const categoryColors: Record<FailureCategory, string> = {
    APPLICATION_BUG: 'bg-red-100 text-red-800',
    TEST_DEFECT: 'bg-yellow-100 text-yellow-800',
    ENVIRONMENT: 'bg-blue-100 text-blue-800',
    FLAKY_TIMING: 'bg-purple-100 text-purple-800',
    DEPENDENCY: 'bg-orange-100 text-orange-800',
    UNKNOWN: 'bg-gray-100 text-gray-800',
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          AI Root Cause Analysis
          <span className="text-xs text-muted-foreground font-normal">
            (confidence: {(analysis.confidence * 100).toFixed(0)}%)
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Badge className={categoryColors[analysis.category]}>
          {analysis.category.replace('_', ' ')}
        </Badge>
        <p className="font-medium">{analysis.rootCause}</p>
        <p className="text-sm text-muted-foreground">{analysis.detailedAnalysis}</p>
        <Separator />
        <div>
          <p className="text-sm font-medium mb-1">Suggested Fix</p>
          <p className="text-sm">{analysis.suggestedFix}</p>
        </div>
        <p className="text-xs text-muted-foreground italic">
          AI suggestions should be verified before applying
        </p>
      </CardContent>
    </Card>
  );
}
```

### 6. Implement `JiraTicketBadge`
```tsx
// components/JiraTicketBadge.tsx
export function JiraTicketBadge({
  ticketKey,
  ticketUrl,
  status,
  onUnlink
}: {
  ticketKey: string | null;
  ticketUrl: string | null;
  status: string | null;
  onUnlink?: () => void;
}) {
  if (!ticketKey) {
    return (
      <Button variant="outline" size="sm" onClick={onCreateTicket}>
        Create JIRA Ticket
      </Button>
    );
  }

  const statusColor = status === 'Done' ? 'text-green-600'
    : status === 'In Progress' ? 'text-blue-600'
    : 'text-gray-600';

  return (
    <div className="flex items-center gap-2">
      <a href={ticketUrl!} target="_blank" rel="noreferrer"
        className="text-blue-600 hover:underline font-mono text-sm">
        {ticketKey}
      </a>
      <span className={`text-xs ${statusColor}`}>{status}</span>
      {onUnlink && (
        <button onClick={onUnlink}
          className="text-xs text-muted-foreground hover:text-destructive">
          Unlink
        </button>
      )}
    </div>
  );
}
```

### 7. WebSocket live updates
```tsx
// hooks/useLiveExecutions.ts
export function useLiveExecutions(projectId: string) {
  const [liveRuns, setLiveRuns] = useState<LiveRunUpdate[]>([]);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      onConnect: () => {
        client.subscribe(`/topic/executions/${projectId}`, (msg) => {
          const update = JSON.parse(msg.body) as LiveRunUpdate;
          setLiveRuns(prev => [update, ...prev].slice(0, 20));
        });
      },
    });
    client.activate();
    return () => { client.deactivate(); };
  }, [projectId]);

  return liveRuns;
}
```

### 8. Component test
```tsx
// components/__tests__/QualityHealthMatrix.test.tsx
import { render, screen } from '@testing-library/react';

test('renders team rows and highlights failing teams', () => {
  const teams = [
    { teamId: '1', teamName: 'Payments', passRate: 0.87, flakyCount: 31,
      brokenCount: 8, trend: -0.054, gate: 'WARN' as const },
    { teamId: '2', teamName: 'Auth', passRate: 0.987, flakyCount: 2,
      brokenCount: 0, trend: 0, gate: 'PASS' as const },
  ];

  render(<QualityHealthMatrix teams={teams} />);

  expect(screen.getByText('Payments')).toBeInTheDocument();
  expect(screen.getByText('87.0%')).toBeInTheDocument();
  expect(screen.getByText('WARN')).toBeInTheDocument();
});
```

## Validation
- Components are pure (no direct API calls — data passed as props or via hooks)
- Loading and error states handled for every `useQuery` call
- Charts use `ResponsiveContainer` — never hardcoded pixel widths
- SLA reference line visible on pass rate chart
- AI analysis card has disclaimer label
- All links to JIRA open in new tab with `rel="noreferrer"`
- Component tests pass with `@testing-library/react`
