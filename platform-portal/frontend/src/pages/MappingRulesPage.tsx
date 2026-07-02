import { useState } from 'react'
import { OrganizationSelect } from '@/components/ScopeSelectors'
import MappingRulesEditor from '@/components/MappingRulesEditor'
import { Boxes } from 'lucide-react'
import { PageHeader } from '@/components/ui'

/**
 * Organization-level mapping rules. The org ruleset overrides the built-in default
 * for every project in the org; each project can further override it (Project →
 * Mapping → Mapping rules).
 */
export default function MappingRulesPage() {
  const [orgId, setOrgId] = useState('')

  return (
    <div className="space-y-6">
      <PageHeader
        title="Mapping Rules"
        icon={<Boxes size={20} />}
        description="The Mapping Suggester’s heuristics (lane keywords, field targets, value maps, formulas). Set an organization default here; projects inherit it and may override their own."
      />

      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-fg">Organization:</label>
        <OrganizationSelect value={orgId} onChange={setOrgId} />
      </div>

      {orgId ? (
        <MappingRulesEditor scope="ORG" id={orgId} />
      ) : (
        <p className="text-sm text-fg-subtle">Select an organization to edit its mapping rules.</p>
      )}
    </div>
  )
}
