import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { relativeTime } from '@/lib/utils'
import LoadingSpinner from '@/components/LoadingSpinner'
import ErrorMessage from '@/components/ErrorMessage'
import { Save, RotateCcw, FileDown, CheckCircle, Boxes } from 'lucide-react'

interface Props {
  scope: 'ORG' | 'PROJECT'
  id: string                 // org id or project id
}

/**
 * Edit the Mapping Suggester ruleset at a scope. Resolution is
 * PROJECT → ORG → built-in default; this editor shows the scope's own override
 * (or the inherited rules if none) and lets the user modify/replace it, reset to
 * the parent/default, or load the built-in default into the editor.
 */
export default function MappingRulesEditor({ scope, id }: Props) {
  const qc = useQueryClient()
  const key = ['mapping-rules', scope, id]
  const [draft, setDraft] = useState('')
  const [dirty, setDirty] = useState(false)
  const [jsonErr, setJsonErr] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const actor = (typeof localStorage !== 'undefined' && localStorage.getItem('platform.actor')) || 'portal'

  const { data, isLoading, error } = useQuery({
    queryKey: key,
    queryFn: () => scope === 'ORG' ? api.orgMappingRules(id) : api.projectMappingRules(id),
    enabled: !!id,
  })

  useEffect(() => {
    if (data && !dirty) setDraft(data.json)
  }, [data, dirty])

  const validate = (text: string): boolean => {
    try { JSON.parse(text); setJsonErr(null); return true }
    catch (e) { setJsonErr((e as Error).message); return false }
  }

  const saveMutation = useMutation({
    mutationFn: () => scope === 'ORG'
      ? api.saveOrgMappingRules(id, draft, actor)
      : api.saveProjectMappingRules(id, draft, actor),
    onSuccess: () => {
      setDirty(false); setSaved(true); setTimeout(() => setSaved(false), 2500)
      void qc.invalidateQueries({ queryKey: key })
    },
  })

  const resetMutation = useMutation({
    mutationFn: () => scope === 'ORG' ? api.resetOrgMappingRules(id) : api.resetProjectMappingRules(id),
    onSuccess: () => { setDirty(false); void qc.invalidateQueries({ queryKey: key }) },
  })

  const loadDefault = async () => {
    const def = await api.mappingRulesDefault()
    setDraft(def.json); setDirty(true); validate(def.json)
  }

  if (isLoading) return <LoadingSpinner message="Loading mapping rules…" />
  if (error || !data) return <ErrorMessage message="Failed to load mapping rules." />

  const parentLabel = scope === 'PROJECT' ? 'organization / default' : 'built-in default'
  const resetVerb = scope === 'PROJECT' ? `Reset to ${data.source === 'ORG' ? 'organization' : 'default'}` : 'Reset to default'

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 flex-wrap text-sm">
        <Boxes size={16} className="text-slate-400" />
        {data.customized ? (
          <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded bg-purple-100 text-purple-700">
            Customized at {scope === 'ORG' ? 'organization' : 'project'} level
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-600">
            Inheriting from {data.source === 'ORG' ? 'organization' : 'default'}
          </span>
        )}
        {data.updatedBy && (
          <span className="text-xs text-slate-400">
            last saved by {data.updatedBy} · {data.updatedAt ? relativeTime(data.updatedAt) : ''}
          </span>
        )}
      </div>

      <textarea
        value={draft}
        spellCheck={false}
        onChange={e => { setDraft(e.target.value); setDirty(true); validate(e.target.value) }}
        className="w-full h-[26rem] text-xs font-mono border border-slate-200 rounded-lg p-3 bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
      />

      {jsonErr && <p className="text-xs text-red-600">Invalid JSON: {jsonErr}</p>}
      {saveMutation.isError && <p className="text-xs text-red-600">{(saveMutation.error as Error).message}</p>}

      <div className="flex items-center gap-2">
        <button
          onClick={() => { if (validate(draft)) saveMutation.mutate() }}
          disabled={!dirty || !!jsonErr || saveMutation.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50">
          <Save size={14} /> {saveMutation.isPending ? 'Saving…' : 'Save'}
        </button>
        <button
          onClick={loadDefault}
          className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50">
          <FileDown size={14} /> Load default
        </button>
        <button
          onClick={() => { if (confirm(`${resetVerb}? This removes this scope's override.`)) resetMutation.mutate() }}
          disabled={!data.customized || resetMutation.isPending}
          title={data.customized ? undefined : 'No override to reset'}
          className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50">
          <RotateCcw size={14} /> {resetMutation.isPending ? 'Resetting…' : resetVerb}
        </button>
        {saved && <span className="text-xs text-green-600 inline-flex items-center gap-1"><CheckCircle size={13} /> Saved</span>}
        {dirty && !saved && <span className="text-xs text-amber-600">Unsaved changes</span>}
      </div>

      <p className="text-xs text-slate-400">
        Rules resolve {scope === 'PROJECT' ? 'project → organization → built-in default' : 'organization → built-in default'}.
        Saving overrides this scope only; “{resetVerb}” falls back to the {parentLabel}.
      </p>
    </div>
  )
}
