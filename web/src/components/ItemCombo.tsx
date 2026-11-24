import { useId, useState } from 'react'
import type { Item } from '../api/types'
import { trLine, useLanguage } from '../i18n/lang'
import { Tr } from '../i18n/Tr'
import { oneLine } from './Name'

/**
 * Item field as a drop-down: tap it, type to narrow the list, tap an item. When nothing matches, a "Create new item" row
 * adds the typed name to the catalogue (only offered when onCreate is given).
 */
export function ItemCombo({ label, value, items, required = false, onSelect, onCreate }: {
  label: string; value: string; items: Item[]; required?: boolean
  onSelect: (id: string) => void; onCreate?: (name: string) => Promise<void>
}) {
  useLanguage()
  const id = useId()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState(false)
  const selected = items.find((i) => i.id === value)
  const q = query.trim().toLowerCase()
  const matches = items.filter((i) => !q || oneLine(i.name).toLowerCase().includes(q))
  const exact = items.some((i) => oneLine(i.name).trim().toLowerCase() === q)
  const keep = (e: { preventDefault: () => void }) => e.preventDefault()
  return (
    <div className="field combo">
      <label htmlFor={id}><Tr k={label} inline />{required ? ' *' : ''}</label>
      <input
        id={id} type="text" role="combobox" aria-expanded={open} aria-controls={`${id}-list`} autoComplete="off"
        value={open ? query : selected ? oneLine(selected.name) : ''}
        placeholder={trLine('Tap to choose or type to search')}
        onFocus={() => { setOpen(true); setQuery('') }}
        onBlur={() => setOpen(false)}
        onChange={(e) => { setQuery(e.target.value); setOpen(true) }}
      />
      {open && (
        <ul id={`${id}-list`} role="listbox" className="combo-list">
          {matches.map((i) => (
            <li key={i.id} role="option" aria-selected={i.id === value}>
              <button type="button" onMouseDown={keep} onClick={() => { onSelect(i.id); setOpen(false) }}>{oneLine(i.name)}</button>
            </li>
          ))}
          {matches.length === 0 && !(onCreate && q) && <li className="muted small combo-empty"><Tr k="No items found" /></li>}
          {onCreate && q && !exact && (
            <li role="option" aria-selected={false}>
              <button type="button" className="combo-create" disabled={busy} onMouseDown={keep}
                onClick={() => { setBusy(true); void onCreate(query.trim()).finally(() => { setBusy(false); setOpen(false) }) }}>
                {trLine('Create new item: %1$s', query.trim())}
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
