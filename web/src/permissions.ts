import { trKey } from './i18n/lang'

/** The permissions the screens can show, grouped like the Android app. Labels are English; Hindi comes from the catalogue. */
export interface PermissionDef { code: string; label: string; group: string }

const g = (group: string, items: Array<[string, string]>): PermissionDef[] => items.map(([code, label]) => ({ code, label, group }))

export const PERMISSIONS: PermissionDef[] = [
  ...g(trKey('Suppliers'), [['SUPPLIER_VIEW', trKey('View suppliers')], ['SUPPLIER_ADD', trKey('Add suppliers')], ['SUPPLIER_EDIT', trKey('Edit suppliers')], ['SUPPLIER_DELETE', trKey('Delete suppliers')]]),
  ...g(trKey('Items'), [['ITEM_VIEW', trKey('View items')], ['ITEM_ADD', trKey('Add items')], ['ITEM_EDIT', trKey('Edit items')], ['ITEM_DELETE', trKey('Delete items')]]),
  ...g(trKey('Out-of-stock lists'), [['LIST_CREATE', trKey('Create out-of-stock lists')], ['LIST_EDIT', trKey('Edit out-of-stock lists')], ['LIST_DELETE', trKey('Delete out-of-stock lists')]]),
  ...g(trKey('Purchases'), [['PURCHASE_RECORD', trKey('Record purchases')], ['PURCHASE_EDIT', trKey('Edit purchases')], ['PURCHASE_DELETE', trKey('Delete or cancel purchases')],
    ['PURCHASE_PRICE_ENTER', trKey('Enter purchase prices')], ['SELLING_PRICE_ENTER', trKey('Enter selling prices')], ['CATALOG_SELLING_PRICE_CHANGE', trKey('Change latest catalogue selling price')], ['PURCHASE_HISTORY_VIEW', trKey('View purchase history')]]),
  ...g(trKey('PDFs and expiry'), [['PDF_GENERATE', trKey('Generate PDFs')], ['PDF_SHARE', trKey('Print and share PDFs')], ['EXPIRY_VIEW', trKey('View expiry tracking')]]),
  ...g(trKey('Sensitive'), [['EXPECTED_PROFIT_VIEW', trKey('View expected profit')], ['PROFIT_REPORT_VIEW', trKey('View profit reports')], ['SETTINGS_MANAGE', trKey('Manage business settings')], ['STAFF_MANAGE', trKey('Manage staff accounts')]]),
]
/** What a newly created staff account gets: the same defaults as the server (and the Android app). */
export const STAFF_DEFAULTS = new Set(['SUPPLIER_VIEW', 'SUPPLIER_ADD', 'SUPPLIER_EDIT', 'ITEM_VIEW', 'ITEM_ADD', 'ITEM_EDIT', 'LIST_CREATE', 'LIST_EDIT', 'PURCHASE_RECORD',
  'PURCHASE_PRICE_ENTER', 'SELLING_PRICE_ENTER', 'PURCHASE_HISTORY_VIEW', 'PDF_GENERATE', 'PDF_SHARE', 'EXPIRY_VIEW'])
export const GROUPS = [...new Set(PERMISSIONS.map((p) => p.group))]

const ACTIONS: Record<string, string> = {
  BUSINESS_REGISTERED: trKey('Registered the business'), BUSINESS_UPDATED: trKey('Changed business details'), LOGIN: trKey('Signed in'), LOGIN_FAILED: trKey('Failed sign-in attempt'),
  ACCOUNT_LOCKED: trKey('Account locked after failed attempts'), LOGOUT: trKey('Signed out'), TOKEN_REUSE_DETECTED: trKey('Suspicious session reuse - session ended'),
  REAUTHENTICATED: trKey('Confirmed password'), PASSWORD_CHANGED: trKey('Changed password'), PASSWORD_RESET_REQUESTED: trKey('Requested a password reset'), PASSWORD_RESET: trKey('Reset password'),
  STAFF_CREATED: trKey('Created a staff account'), STAFF_UPDATED: trKey('Edited a staff account'), STAFF_ACTIVATED: trKey('Activated a staff account'), STAFF_DEACTIVATED: trKey('Deactivated a staff account'),
  STAFF_PASSWORD_RESET: trKey('Reset a staff password'), STAFF_SESSIONS_REVOKED: trKey('Signed a staff member out everywhere'), PERMISSIONS_CHANGED: trKey('Changed staff permissions'),
}
/** English wording of an audit-log action; unknown actions are made readable ("SOMETHING_DONE" → "Something done"). */
export function actionLabel(action: string): { english: string; known: boolean } {
  const k = ACTIONS[action]
  if (k) return { english: k, known: true }
  const x = action.toLowerCase().replace(/_/g, ' ')
  return { english: x.charAt(0).toUpperCase() + x.slice(1), known: false }
}
