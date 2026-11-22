import { trKey } from '../i18n/lang'

export interface NavItem {
  to: string
  label: string
  /** The server permission needed to see this entry (any one of them, when a list). The server still decides. */
  permission?: string | string[]
  /** Shown in the bottom menu; the others are reached from the Home screen. */
  bottom?: boolean
}

const PURCHASE_ANY = ['PURCHASE_HISTORY_VIEW', 'PURCHASE_RECORD', 'PURCHASE_EDIT', 'PURCHASE_DELETE']

export const NAV: NavItem[] = [
  { to: '/', label: trKey('Home'), bottom: true },
  { to: '/lists', label: trKey('Lists'), permission: ['LIST_CREATE', 'LIST_EDIT', 'LIST_DELETE', 'PURCHASE_RECORD'], bottom: true },
  { to: '/purchases', label: trKey('Purchases'), permission: PURCHASE_ANY, bottom: true },
  { to: '/items', label: trKey('Items'), permission: 'ITEM_VIEW', bottom: true },
  { to: '/suppliers', label: trKey('Suppliers'), permission: 'SUPPLIER_VIEW' },
  { to: '/expiry', label: trKey('Expiry'), permission: 'EXPIRY_VIEW' },
  { to: '/account', label: trKey('Account'), bottom: true },
]

export const visibleNav = (items: NavItem[], can: (permission: string) => boolean): NavItem[] =>
  items.filter((i) => !i.permission || [i.permission].flat().some(can))
