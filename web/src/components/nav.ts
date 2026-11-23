import { trKey } from '../i18n/lang'

export interface NavItem {
  to: string
  label: string
  subtitle: string
  icon: string
  /** The server permission needed to see this card (any one of them, when a list). The server still decides. */
  permission?: string | string[]
  /** Shown to the owner only (the server refuses everyone else). */
  ownerOnly?: boolean
}

const PURCHASE_ANY = ['PURCHASE_HISTORY_VIEW', 'PURCHASE_RECORD', 'PURCHASE_EDIT', 'PURCHASE_DELETE']

/** The cards on the Home screen, in the same order and with the same wording as the Android app. */
export const NAV: NavItem[] = [
  { to: '/lists', label: trKey('Out-of-stock lists'), subtitle: trKey('Create, edit and share lists'), icon: '📋', permission: ['LIST_CREATE', 'LIST_EDIT', 'LIST_DELETE', 'PURCHASE_RECORD'] },
  { to: '/purchases', label: trKey('Purchases'), subtitle: trKey('Bills, goods received and deliveries'), icon: '🧾', permission: PURCHASE_ANY },
  { to: '/expiry', label: trKey('Expiry'), subtitle: trKey('Expired and soon-to-expire stock'), icon: '⏳', permission: 'EXPIRY_VIEW' },
  { to: '/reports', label: trKey('Reports'), subtitle: trKey('Totals, prices and history as PDF'), icon: '📊', permission: 'PURCHASE_HISTORY_VIEW' },
  { to: '/items', label: trKey('Items'), subtitle: trKey('Catalogue, units and latest prices'), icon: '📦', permission: 'ITEM_VIEW' },
  { to: '/staff', label: trKey('Staff'), subtitle: trKey('Accounts, permissions and signed-in phones'), icon: '👥', permission: 'STAFF_MANAGE' },
  { to: '/business', label: trKey('Business profile'), subtitle: trKey('Details printed on your PDFs'), icon: '🏪' },
  { to: '/activity', label: trKey('Activity log'), subtitle: trKey('Who did what, newest first'), icon: '🕑', ownerOnly: true },
  { to: '/suppliers', label: trKey('Suppliers'), subtitle: trKey('Contacts of the people you buy from'), icon: '🚚', permission: 'SUPPLIER_VIEW' },
]

export const visibleNav = (items: NavItem[], can: (permission: string) => boolean, isOwner = false): NavItem[] =>
  items.filter((i) => (i.ownerOnly ? isOwner : !i.permission || [i.permission].flat().some(can)))
