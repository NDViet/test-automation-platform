import { useState } from 'react'
import { OrganizationSelect } from '@/components/ScopeSelectors'
import MappingRulesEditor from '@/components/MappingRulesEditor'
import { Boxes } from 'lucide-react'

/**
 * Organization-level mapping rules. The org ruleset overrides the built-in default
 * for every project in the org; each project can further override it (Project →
 * Mapping → Mapping rules).
 */
export default function MappingRulesPage() {
  const [orgId, setOrgId] = useState('')

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
          <Boxes size={22} /> Mapping Rules
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          The Mapping Suggester’s heuristics (lane keywords, field targets, value maps, formulas).
          Set an organization default here; projects inherit it and may override their own.
        </p>
      </div>

      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-slate-700">Organization:</label>
        <OrganizationSelect value={orgId} onChange={setOrgId} />
      </div>

      {orgId ? (
        <MappingRulesEditor scope="ORG" id={orgId} />
      ) : (
        <p className="text-sm text-slate-400">Select an organization to edit its mapping rules.</p>
      )}
    </div>
  )
}
