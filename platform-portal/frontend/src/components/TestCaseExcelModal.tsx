import { useState, useRef } from 'react'
import * as XLSX from 'xlsx'
import { X, Upload, FileSpreadsheet, ChevronRight, Loader2, CheckCircle, AlertTriangle, Download } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { ManagedTestCase } from '@/lib/types'

// ── Column mapping table ──────────────────────────────────────────────────────

/** Maps normalised column header → platform field key */
const KNOWN_COLUMNS: Record<string, string> = {
  'test id': 'externalId',
  'id': 'externalId',
  'external id': 'externalId',
  'test_id': 'externalId',
  'title': 'title',
  'test case title': 'title',
  'test name': 'title',
  'name': 'title',
  'preconditions': 'preconditions',
  'precondition': 'preconditions',
  'pre-conditions': 'preconditions',
  'prerequisites': 'preconditions',
  'test steps': 'steps',
  'steps': 'steps',
  'step': 'steps',
  'test step': 'steps',
  'expected result': 'expectedResult',
  'expected results': 'expectedResult',
  'expected': 'expectedResult',
  'expected outcome': 'expectedResult',
  'priority': 'priority',
  'notes': 'description',
  'description': 'description',
  'note': 'description',
  'comments': 'description',
}

/** Columns that are present in common test suite files but have no matching field — skip them without asking the user */
const SKIP_COLUMNS = new Set([
  'feature', 'complexity', 'smoke test', 'test date', 'date', 'result',
  'run count', 'jira ticket', 'jira', 'automation status', 'assign qa',
  'assigned qa', 'last updated', 'updated', 'column1', 'column2', 'column3',
])

const PLATFORM_FIELDS = [
  { key: 'title',          label: 'Title (required)' },
  { key: 'externalId',     label: 'Test ID' },
  { key: 'preconditions',  label: 'Preconditions' },
  { key: 'steps',          label: 'Test Steps' },
  { key: 'expectedResult', label: 'Expected Result' },
  { key: 'priority',       label: 'Priority' },
  { key: 'description',    label: 'Description / Notes' },
  { key: '_skip',          label: '— Skip this column —' },
]

// ── Helpers ───────────────────────────────────────────────────────────────────

function normKey(s: string) { return s.toLowerCase().trim() }

function isMetaHeaderRow(row: (string | null | undefined)[]): boolean {
  // Detect Excel "Column1, Column2…" auto-generated headers
  return row.every((v, i) => !v || normKey(String(v)) === `column${i + 1}`)
}

function parsePriority(raw: string | null | undefined): string {
  if (!raw) return 'MEDIUM'
  const v = raw.trim().toUpperCase()
  if (v === 'P0' || v === 'CRITICAL')    return 'CRITICAL'
  if (v === 'P1' || v === 'HIGH')        return 'HIGH'
  if (v === 'P2' || v === 'MEDIUM' || v === 'MED') return 'MEDIUM'
  if (v === 'P3' || v === 'LOW')         return 'LOW'
  return 'MEDIUM'
}

interface StepInput { action: string; expectedResult?: string }

function parseSteps(raw: string | null | undefined): StepInput[] {
  if (!raw?.trim()) return []
  const lines = raw.split('\n').map(l => l.trim()).filter(Boolean)
  // Numbered: "1. ..." or "1) ..."
  const numbered = lines.filter(l => /^\d+[.)]\s/.test(l))
  if (numbered.length > 0) {
    return numbered.map(l => ({ action: l.replace(/^\d+[.)]\s*/, '').trim() }))
  }
  // Bullet: "- ..." or "• ..."
  const bullets = lines.filter(l => /^[-•]\s/.test(l))
  if (bullets.length > 0) {
    return bullets.map(l => ({ action: l.replace(/^[-•]\s*/, '').trim() }))
  }
  // Fall back: whole text as single step
  return [{ action: raw.trim() }]
}

function stepsToText(steps: { action: string; expectedResult?: string | null }[]): string {
  return steps.map((s, i) =>
    `${i + 1}. ${s.action}${s.expectedResult ? `\n   → ${s.expectedResult}` : ''}`
  ).join('\n')
}

// ── Template download ─────────────────────────────────────────────────────────

export function downloadTemplate() {
  const wb = XLSX.utils.book_new()

  // ── Sheet 1: Test Cases ──
  const headers = ['Test ID', 'Title', 'Preconditions', 'Test Steps', 'Expected Result', 'Priority', 'Notes']
  const examples = [
    [
      'REG-001',
      'User completes registration from homepage',
      'User is not registered\nBrowser is on homepage',
      '1. Navigate to homepage\n2. Click "Join for free"\n3. Enter email\n4. Enter password\n5. Submit OTP',
      '- Registration completes successfully\n- User is logged in',
      'P1',
      'Smoke test candidate',
    ],
    [
      'LOGIN-001',
      'User logs in with valid credentials',
      'User is registered\nUser is not logged in',
      '1. Navigate to login page\n2. Enter email\n3. Enter password\n4. Click Login',
      '- User is redirected to dashboard\n- Session cookie is set',
      'P0',
      '',
    ],
  ]

  const ws = XLSX.utils.aoa_to_sheet([headers, ...examples])

  // Column widths
  ws['!cols'] = [
    { wch: 12 }, { wch: 40 }, { wch: 35 }, { wch: 50 },
    { wch: 40 }, { wch: 10 }, { wch: 25 },
  ]

  XLSX.utils.book_append_sheet(wb, ws, 'Test Cases')

  // ── Sheet 2: Instructions ──
  const instrRows = [
    ['Field', 'Required', 'Valid Values / Format', 'Notes'],
    ['Test ID', 'No', 'Any text e.g. REG-001', 'Unique identifier. Leave blank to auto-assign.'],
    ['Title', 'YES', 'Any text', 'Short, descriptive name for the test case.'],
    ['Preconditions', 'No', 'Free text (multi-line)', 'System state required before running the test.'],
    ['Test Steps', 'No', '1. Step one\n2. Step two\n3. Step three', 'Numbered list. Each step on a new line.'],
    ['Expected Result', 'No', 'Free text (multi-line)', 'Overall pass condition for the test.'],
    ['Priority', 'No', 'P0 / P1 / P2 / P3\nor CRITICAL / HIGH / MEDIUM / LOW', 'Default: P2 (MEDIUM) if blank.'],
    ['Notes', 'No', 'Any text', 'Extra context or comments. Stored as Description.'],
    [],
    ['Priority mapping', '', '', ''],
    ['P0', '', 'CRITICAL', ''],
    ['P1', '', 'HIGH', ''],
    ['P2', '', 'MEDIUM (default)', ''],
    ['P3', '', 'LOW', ''],
  ]
  const instrWs = XLSX.utils.aoa_to_sheet(instrRows)
  instrWs['!cols'] = [{ wch: 16 }, { wch: 10 }, { wch: 35 }, { wch: 45 }]
  XLSX.utils.book_append_sheet(wb, instrWs, 'Instructions')

  XLSX.writeFile(wb, 'test-cases-template.xlsx')
}

// ── Export existing test cases ────────────────────────────────────────────────

export function exportTestCases(cases: ManagedTestCase[], filename = 'test-cases-export.xlsx') {
  const headers = [
    'Test ID', 'Title', 'Status', 'Priority',
    'Preconditions', 'Test Steps', 'Expected Result',
    'Description', 'Automation Status', 'Created At',
  ]
  const rows = cases.map(tc => [
    tc.externalId ?? '',
    tc.title,
    tc.status,
    tc.priority,
    tc.preconditions ?? '',
    tc.steps?.length ? stepsToText(tc.steps) : '',
    tc.expectedResult ?? '',
    tc.description ?? '',
    tc.automationStatus ?? '',
    tc.createdAt ? new Date(tc.createdAt).toLocaleDateString() : '',
  ])

  const wb = XLSX.utils.book_new()
  const ws = XLSX.utils.aoa_to_sheet([headers, ...rows])
  ws['!cols'] = [
    { wch: 12 }, { wch: 40 }, { wch: 14 }, { wch: 10 },
    { wch: 35 }, { wch: 50 }, { wch: 40 }, { wch: 30 }, { wch: 16 }, { wch: 14 },
  ]
  XLSX.utils.book_append_sheet(wb, ws, 'Test Cases')
  XLSX.writeFile(wb, filename)
}

// ── Parsed row type ───────────────────────────────────────────────────────────

interface ParsedRow {
  title: string
  externalId?: string
  preconditions?: string
  steps: StepInput[]
  expectedResult?: string
  priority: string
  description?: string
}

// ── Import modal steps ────────────────────────────────────────────────────────

type Step =
  | 'upload'
  | 'sheet-select'
  | 'column-map'
  | 'preview'
  | 'importing'
  | 'done'

interface WorkbookState {
  wb: XLSX.WorkBook
  fileName: string
}

interface SheetData {
  sheetName: string
  headers: string[]
  rows: (string | null)[][]
  /** Columns that couldn't be auto-mapped */
  unmappedCols: string[]
}

// ── Main component ────────────────────────────────────────────────────────────

interface Props {
  onClose: () => void
  onImport: (rows: ParsedRow[]) => Promise<{ ok: number; failed: number }>
}

export function ExcelImportModal({ onClose, onImport }: Props) {
  const fileRef = useRef<HTMLInputElement>(null)
  const [step, setStep] = useState<Step>('upload')
  const [wbState, setWbState] = useState<WorkbookState | null>(null)
  const [sheetData, setSheetData] = useState<SheetData | null>(null)
  const [columnMap, setColumnMap] = useState<Record<string, string>>({})
  const [parsed, setParsed] = useState<ParsedRow[]>([])
  const [dragOver, setDragOver] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [progress, setProgress] = useState({ done: 0, total: 0 })
  const [result, setResult] = useState({ ok: 0, failed: 0 })

  // ── File handling ──

  function loadFile(file: File) {
    setError(null)
    const reader = new FileReader()
    reader.onload = e => {
      try {
        const data = new Uint8Array(e.target!.result as ArrayBuffer)
        const wb = XLSX.read(data, { type: 'array' })
        setWbState({ wb, fileName: file.name })

        if (wb.SheetNames.length === 1) {
          loadSheet(wb, wb.SheetNames[0])
        } else {
          setStep('sheet-select')
        }
      } catch {
        setError('Failed to read file. Make sure it is a valid .xlsx or .xls file.')
      }
    }
    reader.readAsArrayBuffer(file)
  }

  function loadSheet(wb: XLSX.WorkBook, sheetName: string) {
    const ws = wb.Sheets[sheetName]
    const raw = XLSX.utils.sheet_to_json<(string | null)[]>(ws, { header: 1, defval: null }) as (string | null)[][]

    if (raw.length < 2) {
      setError('Sheet is empty or has no data rows.')
      return
    }

    // Detect fake meta-header row (Column1, Column2 ...)
    let headerRowIdx = 0
    if (raw.length >= 2 && isMetaHeaderRow(raw[0] as string[])) {
      headerRowIdx = 1
    }

    const headers = (raw[headerRowIdx] as (string | null)[])
      .map(h => h?.trim() ?? '')
      .filter(Boolean)

    const dataRows = raw.slice(headerRowIdx + 1).filter(row =>
      row.some(cell => cell !== null && String(cell).trim() !== '')
    ) as (string | null)[][]

    // Auto-map columns
    const autoMap: Record<string, string> = {}
    const unmapped: string[] = []
    headers.forEach(h => {
      const norm = normKey(h)
      if (KNOWN_COLUMNS[norm]) {
        autoMap[h] = KNOWN_COLUMNS[norm]
      } else if (!SKIP_COLUMNS.has(norm)) {
        unmapped.push(h)
        autoMap[h] = '_skip' // default: skip
      }
      // else: silently skip
    })

    setSheetData({ sheetName, headers, rows: dataRows, unmappedCols: unmapped })
    setColumnMap(autoMap)

    if (unmapped.length > 0) {
      setStep('column-map')
    } else {
      buildPreview(autoMap, headers, dataRows)
    }
  }

  // ── Column mapping → preview ──

  function buildPreview(map: Record<string, string>, headers: string[], rows: (string | null)[][]) {
    const fieldForHeader = (h: string) => map[h]

    const result: ParsedRow[] = []
    for (const row of rows) {
      const get = (field: string): string | null => {
        const idx = headers.findIndex(h => fieldForHeader(h) === field)
        if (idx === -1) return null
        const val = row[idx]
        return val != null ? String(val).trim() : null
      }

      const title = get('title')
      if (!title) continue  // skip rows without a title

      result.push({
        title,
        externalId: get('externalId') ?? undefined,
        preconditions: get('preconditions') ?? undefined,
        steps: parseSteps(get('steps')),
        expectedResult: get('expectedResult') ?? undefined,
        priority: parsePriority(get('priority')),
        description: get('description') ?? undefined,
      })
    }

    setParsed(result)
    setStep('preview')
  }

  function applyColumnMap() {
    if (!sheetData) return
    buildPreview(columnMap, sheetData.headers, sheetData.rows)
  }

  // ── Import ──

  async function startImport() {
    setProgress({ done: 0, total: parsed.length })
    setStep('importing')
    const res = await onImport(parsed)
    setResult(res)
    setStep('done')
  }

  // ── Render helpers ──

  const STEP_LABELS: Record<Step, string> = {
    upload: 'Upload',
    'sheet-select': 'Select Sheet',
    'column-map': 'Map Columns',
    preview: 'Preview',
    importing: 'Importing',
    done: 'Done',
  }
  const STEP_ORDER: Step[] = ['upload', 'sheet-select', 'column-map', 'preview', 'importing', 'done']
  const stepIdx = STEP_ORDER.indexOf(step)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 flex flex-col max-h-[90vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-200 shrink-0">
          <div className="flex items-center gap-2.5">
            <FileSpreadsheet size={18} className="text-green-600" />
            <h2 className="font-semibold text-slate-900">Import Test Cases from Excel</h2>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X size={18} /></button>
        </div>

        {/* Step breadcrumb */}
        {step !== 'done' && step !== 'importing' && (
          <div className="flex items-center gap-1 px-5 py-2 border-b border-slate-100 shrink-0 text-xs text-slate-400">
            {(['upload', 'preview'] as Step[])
              .filter(s => !(s === 'sheet-select' && wbState?.wb.SheetNames.length === 1))
              .map((s, i, arr) => (
                <span key={s} className="flex items-center gap-1">
                  <span className={cn(stepIdx >= STEP_ORDER.indexOf(s) ? 'text-blue-600 font-medium' : '')}>
                    {STEP_LABELS[s]}
                  </span>
                  {i < arr.length - 1 && <ChevronRight size={12} />}
                </span>
              ))}
          </div>
        )}

        {/* Body */}
        <div className="flex-1 overflow-y-auto">

          {/* ── Step: upload ── */}
          {step === 'upload' && (
            <div className="p-6 space-y-4">
              {error && (
                <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-4 py-3">
                  <AlertTriangle size={14} className="shrink-0" />
                  {error}
                </div>
              )}
              <div
                onDragOver={e => { e.preventDefault(); setDragOver(true) }}
                onDragLeave={() => setDragOver(false)}
                onDrop={e => {
                  e.preventDefault()
                  setDragOver(false)
                  const file = e.dataTransfer.files[0]
                  if (file) loadFile(file)
                }}
                onClick={() => fileRef.current?.click()}
                className={cn(
                  'border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-colors',
                  dragOver ? 'border-blue-400 bg-blue-50' : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50'
                )}
              >
                <Upload size={28} className="mx-auto text-slate-300 mb-3" />
                <p className="text-sm font-medium text-slate-600">Drop an Excel file here, or click to browse</p>
                <p className="text-xs text-slate-400 mt-1">.xlsx and .xls files supported</p>
              </div>
              <input
                ref={fileRef}
                type="file"
                accept=".xlsx,.xls"
                className="hidden"
                onChange={e => { const f = e.target.files?.[0]; if (f) loadFile(f) }}
              />
              <p className="text-xs text-slate-400 text-center">
                Not sure about the format?{' '}
                <button onClick={downloadTemplate} className="text-blue-600 hover:underline font-medium">
                  Download the template
                </button>
              </p>
            </div>
          )}

          {/* ── Step: sheet-select ── */}
          {step === 'sheet-select' && wbState && (
            <div className="p-6 space-y-4">
              <p className="text-sm text-slate-600">
                The file <span className="font-medium">{wbState.fileName}</span> has multiple sheets.
                Select the one that contains your test cases.
              </p>
              <div className="space-y-2">
                {wbState.wb.SheetNames.map(name => (
                  <button
                    key={name}
                    onClick={() => loadSheet(wbState.wb, name)}
                    className="w-full flex items-center gap-3 px-4 py-3 rounded-lg border border-slate-200 hover:border-blue-400 hover:bg-blue-50 transition-colors text-left"
                  >
                    <FileSpreadsheet size={16} className="text-green-500 shrink-0" />
                    <span className="text-sm font-medium text-slate-800">{name}</span>
                  </button>
                ))}
              </div>
              {error && (
                <p className="text-xs text-red-600">{error}</p>
              )}
            </div>
          )}

          {/* ── Step: column-map ── */}
          {step === 'column-map' && sheetData && (
            <div className="p-6 space-y-4">
              <p className="text-sm text-slate-600">
                Some columns couldn't be auto-detected. Map them to platform fields or skip them.
              </p>
              <div className="divide-y divide-slate-100 border border-slate-200 rounded-lg overflow-hidden">
                <div className="grid grid-cols-2 px-4 py-2 bg-slate-50 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  <span>Column in file</span>
                  <span>Map to field</span>
                </div>
                {sheetData.unmappedCols.map(col => (
                  <div key={col} className="grid grid-cols-2 px-4 py-2.5 items-center gap-3">
                    <span className="text-sm text-slate-700 font-mono truncate">{col}</span>
                    <select
                      value={columnMap[col] ?? '_skip'}
                      onChange={e => setColumnMap(prev => ({ ...prev, [col]: e.target.value }))}
                      className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      {PLATFORM_FIELDS.map(f => <option key={f.key} value={f.key}>{f.label}</option>)}
                    </select>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Step: preview ── */}
          {step === 'preview' && (
            <div className="p-6 space-y-4">
              <div className="flex items-center gap-3 bg-blue-50 border border-blue-200 rounded-lg px-4 py-3">
                <FileSpreadsheet size={16} className="text-blue-600 shrink-0" />
                <p className="text-sm text-blue-800">
                  <span className="font-semibold">{parsed.length}</span> test case{parsed.length !== 1 ? 's' : ''} ready to import
                  {parsed.filter(r => !r.title).length > 0 && (
                    <span className="ml-2 text-amber-700">
                      ({parsed.filter(r => !r.title).length} rows skipped — no title)
                    </span>
                  )}
                </p>
              </div>

              <div className="border border-slate-200 rounded-lg overflow-hidden">
                <table className="w-full text-xs">
                  <thead className="bg-slate-50 border-b border-slate-200">
                    <tr>
                      <th className="px-3 py-2 text-left font-semibold text-slate-500 uppercase tracking-wide">#</th>
                      <th className="px-3 py-2 text-left font-semibold text-slate-500 uppercase tracking-wide">Test ID</th>
                      <th className="px-3 py-2 text-left font-semibold text-slate-500 uppercase tracking-wide">Title</th>
                      <th className="px-3 py-2 text-left font-semibold text-slate-500 uppercase tracking-wide">Priority</th>
                      <th className="px-3 py-2 text-left font-semibold text-slate-500 uppercase tracking-wide">Steps</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {parsed.slice(0, 8).map((row, i) => (
                      <tr key={i} className="hover:bg-slate-50">
                        <td className="px-3 py-2 text-slate-400 tabular-nums">{i + 1}</td>
                        <td className="px-3 py-2 font-mono text-slate-500">{row.externalId ?? '—'}</td>
                        <td className="px-3 py-2 text-slate-800 max-w-[220px] truncate" title={row.title}>{row.title}</td>
                        <td className="px-3 py-2">
                          <span className={cn(
                            'px-1.5 py-0.5 rounded text-[10px] font-medium',
                            row.priority === 'CRITICAL' ? 'bg-red-100 text-red-700' :
                            row.priority === 'HIGH'     ? 'bg-orange-100 text-orange-700' :
                            row.priority === 'MEDIUM'   ? 'bg-yellow-100 text-yellow-700' :
                                                          'bg-slate-100 text-slate-600'
                          )}>
                            {row.priority}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-slate-500 tabular-nums">{row.steps.length}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {parsed.length > 8 && (
                  <div className="px-3 py-2 text-xs text-slate-400 bg-slate-50 border-t border-slate-100 text-center">
                    …and {parsed.length - 8} more rows
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ── Step: importing ── */}
          {step === 'importing' && (
            <div className="p-8 text-center space-y-4">
              <Loader2 size={28} className="animate-spin mx-auto text-blue-500" />
              <p className="text-sm font-medium text-slate-700">Importing test cases…</p>
              <p className="text-xs text-slate-400">
                {progress.done} / {progress.total} completed
              </p>
              <div className="w-full bg-slate-100 rounded-full h-1.5 max-w-sm mx-auto overflow-hidden">
                <div
                  className="h-full bg-blue-500 rounded-full transition-all"
                  style={{ width: progress.total > 0 ? `${(progress.done / progress.total) * 100}%` : '0%' }}
                />
              </div>
            </div>
          )}

          {/* ── Step: done ── */}
          {step === 'done' && (
            <div className="p-8 text-center space-y-4">
              <div className="w-14 h-14 rounded-full bg-green-100 flex items-center justify-center mx-auto">
                <CheckCircle size={26} className="text-green-600" />
              </div>
              <p className="text-base font-semibold text-slate-900">Import complete</p>
              <div className="flex justify-center gap-6 text-sm">
                <div className="text-center">
                  <p className="text-2xl font-bold text-green-700">{result.ok}</p>
                  <p className="text-xs text-slate-500">imported</p>
                </div>
                {result.failed > 0 && (
                  <div className="text-center">
                    <p className="text-2xl font-bold text-red-600">{result.failed}</p>
                    <p className="text-xs text-slate-500">failed</p>
                  </div>
                )}
              </div>
              <p className="text-xs text-slate-400">Test cases are created as DRAFT — submit them for review to approve.</p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-5 py-4 border-t border-slate-200 flex justify-between items-center shrink-0 gap-3">
          <div>
            {step === 'upload' && (
              <button
                onClick={downloadTemplate}
                className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-700 transition-colors"
              >
                <Download size={13} /> Download template
              </button>
            )}
          </div>
          <div className="flex gap-2 ml-auto">
            {step !== 'importing' && step !== 'done' && (
              <button onClick={onClose}
                className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50">
                Cancel
              </button>
            )}
            {step === 'column-map' && (
              <button
                onClick={applyColumnMap}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 flex items-center gap-2"
              >
                Continue <ChevronRight size={14} />
              </button>
            )}
            {step === 'preview' && parsed.length > 0 && (
              <button
                onClick={startImport}
                className="px-4 py-2 text-sm font-medium text-white bg-green-600 rounded-lg hover:bg-green-700 flex items-center gap-2"
              >
                <Upload size={14} />
                Import {parsed.length} test case{parsed.length !== 1 ? 's' : ''}
              </button>
            )}
            {step === 'done' && (
              <button onClick={onClose}
                className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700">
                Close
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
