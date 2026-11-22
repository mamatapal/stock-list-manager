import { useState } from 'react'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

/** Delete with a second tap to confirm. [what] is an English text such as "this supplier". */
export function DeleteButton({ onConfirm, busy }: { onConfirm: () => void; busy?: boolean }) {
  const [asking, setAsking] = useState(false)
  useLanguage()
  if (!asking) return <button type="button" className="danger" onClick={() => setAsking(true)}>{trLine('Delete')}</button>
  return (
    <div className="card confirm" role="alertdialog">
      <p><Tr k="Delete this? It will be hidden everywhere but kept for history." /></p>
      <button type="button" className="danger" disabled={busy} onClick={onConfirm}>{trLine('Yes, delete')}</button>
      <button type="button" onClick={() => setAsking(false)}>{trLine('Cancel')}</button>
    </div>
  )
}
