import { trKey } from '../i18n/lang'

export interface NavItem {
  to: string
  label: string
  /** The server permission needed to see this entry (the server still decides; this only hides what would be refused). */
  permission?: string | string[]
}

/** The bottom menu. New screens are added here, each with the permission it needs. */
export const NAV: NavItem[] = [
  { to: '/', label: trKey('Home') },
  { to: '/lists', label: trKey('Lists'), permission: ['LIST_CREATE', 'LIST_EDIT', 'LIST_DELETE', 'PURCHASE_RECORD'] },
  { to: '/items', label: trKey('Items'), permission: 'ITEM_VIEW' },
  { to: '/suppliers', label: trKey('Suppliers'), permission: 'SUPPLIER_VIEW' },
  { to: '/account', label: trKey('Account') },
]

export const visibleNav = (items: NavItem[], can: (permission: string) => boolean): NavItem[] =>
  items.filter((i) => !i.permission || [i.permission].flat().some(can))
