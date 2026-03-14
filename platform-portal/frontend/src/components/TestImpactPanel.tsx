import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'
import { TestImpactResult } from '@/lib/types'
import { Zap, AlertTriangle, CheckCircle, Copy, ChevronDown, ChevronUp } from 'lucide-react'

interface Props {
  projectId: string
}

const RISK_CONFIG: Record<TestImpactResult['riskLevel'], { label: string; color: string; icon: typeof CheckCircle }> = {
  LOW:      { label: 'Low Risk',      color: 'text-green-700 bg-green-100 border-green-200',  icon: CheckCircle },
  MEDIUM:   { label: 'Medium Risk',   color: 'text-yellow-700 bg-yellow-100 border-yellow-200', icon: AlertTriangle },
  HIGH:     { label: 'High Risk',     color: 'text-orange-700 bg-orange-100 border-orange-200', icon: AlertTriangle },
  CRITICAL: { label: 'Critical Risk', color: 'text-red-700 bg-red-100 border-red-200',        icon: AlertTriangle },
}

export default function TestImpactPanel({ projectId }: Props) {
  const [filesInput, setFilesInput] = useState('')
  const [submitted, setSubmitted] = useState<string[]>([])
  const [showTests, setShowTests] = useState(false)
  const [copiedFilter, setCopiedFilter] = useState<string | null>(null)

  const { data: summary } = useQuery({
    queryKey: ['impactSummary', projectId],
    queryFn:  () => api.impactSummary(projectId),
  })

  const { data: impact, isLoading } = useQuery({
    queryKey: ['impact', projectId, submitted],
    queryFn:  () => api.testImpact(projectId, submitted),
    enabled:  submitted.length > 0,
  })

  const handleAnalyse = () => {
    const files = filesInput
      .split('\n')
      .map(l => l.trim())
      .filter(Boolean)
    setSubmitted(files)
    setShowTests(false)
  }

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard.writeText(text)
    setCopiedFilter(key)
    setTimeout(() => setCopiedFilter(null), 2000)
  }

  if (!summary?.tiaEnabled) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
        <div className="flex items-center gap-2 mb-2">
          <Zap size={16} className="text-slate-400" />
          <h2 className="font-semibold text-slate-900">Test Impact Analysis</h2>
        </div>
        <p className="text-sm text-slate-500">
          No coverage mappings yet. Add{' '}
          <code className="bg-slate-100 px-1 rounded text-xs">@AffectedBy</code> annotations
          to your Java tests or use <code className="bg-slate-100 px-1 rounded text-xs">coveredModules</code> in
          the Playwright reporter to enable smart test selection.
        </p>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Zap size={16} className="text-blue-600" />
          <h2 className="font-semibold text-slate-900">Test Impact Analysis</h2>
        </div>
        <div className="flex items-center gap-3 text-xs text-slate-500">
          <span>{summary.mappedTests} tests mapped</span>
          <span>·</span>
          <span>{summary.mappedClasses} classes</span>
        </div>
      </div>

      <div className="p-5 space-y-4">
        {/* File input */}
        <div>
          <label className="block text-xs font-medium text-slate-600 mb-1">
            Changed files (one per line, or paste git diff output)
          </label>
          <textarea
            className="w-full h-24 text-xs font-mono border border-slate-200 rounded-lg p-2 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder={`src/main/java/com/example/PaymentService.java\nsrc/main/java/com/example/CartService.java`}
            value={filesInput}
            onChange={e => setFilesInput(e.target.value)}
          />
          <button
            onClick={handleAnalyse}
            disabled={!filesInput.trim() || isLoading}
            className="mt-2 px-4 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {isLoading ? 'Analysing…' : 'Analyse Impact'}
          </button>
        </div>

        {/* Results */}
        {impact && submitted.length > 0 && (
          <div className="space-y-3">
            {/* Summary row */}
            <div className="flex items-center gap-3 flex-wrap">
              <div className={cn(
                'flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-medium',
                RISK_CONFIG[impact.riskLevel].color
              )}>
                {(() => {
                  const Icon = RISK_CONFIG[impact.riskLevel].icon
                  return <Icon size={13} />
                })()}
                {RISK_CONFIG[impact.riskLevel].label}
              </div>
              <div className="flex items-center gap-4 text-sm">
                <span>
                  <span className="font-semibold text-blue-600">{impact.selectedTests}</span>
                  <span className="text-slate-500"> / {impact.totalTests} tests</span>
                </span>
                <span className="font-semibold text-green-600">{impact.estimatedReduction} reduction</span>
              </div>
            </div>

            {/* Uncovered warning */}
            {impact.uncoveredChangedClasses.length > 0 && (
              <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">
                <p className="text-xs font-medium text-amber-800 mb-1">
                  {impact.uncoveredChangedClasses.length} changed class(es) have no coverage mapping — consider running full suite:
                </p>
                <ul className="space-y-0.5">
                  {impact.uncoveredChangedClasses.map(cls => (
                    <li key={cls} className="text-xs font-mono text-amber-700">• {cls}</li>
                  ))}
                </ul>
              </div>
            )}

            {/* Filter strings */}
            {impact.mavenFilter && (
              <div className="space-y-2">
                <p className="text-xs font-medium text-slate-600">CI filter commands</p>
                {[
                  { label: 'Maven', cmd: `mvn test -Dtest="${impact.mavenFilter}"`, key: 'maven' },
                  { label: 'Gradle', cmd: `./gradlew test ${impact.gradleFilter}`, key: 'gradle' },
                ].map(({ label, cmd, key }) => (
                  <div key={key} className="flex items-start gap-2">
                    <span className="text-xs text-slate-400 w-12 shrink-0 pt-1">{label}</span>
                    <div className="flex-1 bg-slate-900 rounded-lg px-3 py-2 flex items-center justify-between gap-2 min-w-0">
                      <code className="text-xs text-green-400 truncate">{cmd}</code>
                      <button
                        onClick={() => copyToClipboard(cmd, key)}
                        className="shrink-0 text-slate-400 hover:text-white transition-colors"
                      >
                        {copiedFilter === key
                          ? <CheckCircle size={13} className="text-green-400" />
                          : <Copy size={13} />}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Recommended tests list */}
            {impact.recommendedTests.length > 0 && (
              <div>
                <button
                  onClick={() => setShowTests(v => !v)}
                  className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 font-medium"
                >
                  {showTests ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
                  {showTests ? 'Hide' : 'Show'} {impact.recommendedTests.length} recommended tests
                </button>
                {showTests && (
                  <div className="mt-2 max-h-48 overflow-y-auto rounded-lg border border-slate-100 divide-y divide-slate-50">
                    {impact.recommendedTests.map(t => (
                      <div key={t} className="px-3 py-2 text-xs font-mono text-slate-700 truncate" title={t}>
                        {t.split('.').pop() ?? t}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
