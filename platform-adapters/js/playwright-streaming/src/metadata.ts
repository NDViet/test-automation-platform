/**
 * In-test metadata API for the Test Automation Platform.
 *
 * Call these from inside your Playwright test functions (or beforeEach hooks) to
 * declare what the test exercises and attach structured labels. The reporter picks
 * up all annotations automatically — no extra configuration needed.
 *
 * @example
 * ```ts
 * import { tia } from '@platform/playwright-streaming-reporter/metadata'
 *
 * test('should process payment', async ({ page }) => {
 *   // Declare which source files this test exercises (for TIA change-based selection)
 *   tia.covers(['src/payment/PaymentProcessor.ts', 'src/api/checkout.ts'])
 *   tia.component('checkout', 'payment')
 *   tia.route('POST /api/orders', 'GET /api/cart')
 *
 *   // Attach labels for filtering and ownership
 *   tia.owner('payments-team')
 *   tia.jira('PROJ-123')
 *   tia.label('priority', 'critical')
 *
 *   // ... test code
 * })
 * ```
 */

import { test as pwTest } from '@playwright/test'

function push(type: string, description?: string): void {
  pwTest.info().annotations.push({ type, description })
}

export const tia = {
  /**
   * Declare source files exercised by this test.
   * Used by TIA to determine which tests to run when a file changes.
   *
   * Paths should be relative to the repository root, e.g.
   * `'src/components/Checkout.tsx'` not `'/absolute/path/…'`.
   */
  covers(files: string | string[]): void {
    const arr = Array.isArray(files) ? files : [files]
    for (const f of arr) push('tia:file', f)
  },

  /**
   * Declare logical components or modules this test exercises.
   * More stable than file paths — use when you want TIA scoped to a feature area
   * rather than individual files.
   *
   * @example tia.component('checkout', 'payment-gateway')
   */
  component(...names: string[]): void {
    for (const n of names) push('tia:component', n)
  },

  /**
   * Declare HTTP or UI routes this test exercises.
   * Lets TIA select this test when backend route handlers change.
   *
   * @example tia.route('POST /api/orders', 'GET /api/cart')
   */
  route(...patterns: string[]): void {
    for (const p of patterns) push('tia:route', p)
  },

  // ── Structured labels ────────────────────────────────────────────────────

  /**
   * Attach an arbitrary key-value label to this test result.
   * Labels appear as filterable metadata in the portal.
   *
   * @example tia.label('priority', 'critical')
   * @example tia.label('feature', 'dark-mode')
   */
  label(key: string, value: string): void {
    push(`label:${key}`, value)
  },

  /**
   * Shorthand for `tia.label('owner', name)`.
   * Identifies which team or person is responsible for this test.
   */
  owner(name: string): void {
    this.label('owner', name)
  },

  /**
   * Shorthand for `tia.label('jira', ticketId)`.
   * Links this test to one or more Jira (or other tracker) tickets.
   *
   * @example tia.jira('PROJ-123', 'PROJ-456')
   */
  jira(...ids: string[]): void {
    for (const id of ids) this.label('jira', id)
  },
}
