import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { units } from '../api/catalogue'
import { useAuth } from '../auth/AuthContext'
import { BilingualNameFields } from '../components/BilingualNameFields'
import { DeleteButton } from '../components/DeleteButton'
import { ErrorText, fieldMessage, hasFieldErrors } from '../components/ErrorText'
import { Name } from '../components/Name'
import { Loading, TextField } from '../components/ui'
import { useLoad } from '../components/useLoad'
import { clean } from '../i18n/bilingualName'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

export function UnitsPage() {
  const { can } = useAuth()
  useLanguage()
  const { data, error, loading, reload } = useLoad(() => units.list(), [])
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [formError, setFormError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const canAdd = can('ITEM_ADD') || can('ITEM_EDIT')

  async function add(e: FormEvent) {
    e.preventDefault()
    if (!code.trim() || !clean(name)) { setFormError('Enter a short code and a name'); return }
    setBusy(true); setFormError(null)
    try { await units.create({ code: code.trim(), name: clean(name) }); setCode(''); setName(''); reload() } catch (err) { setFormError(err) } finally { setBusy(false) }
  }

  async function remove(id: string, version: number) {
    setBusy(true)
    try { await units.remove(id, version); reload() } catch (err) { setFormError(err) } finally { setBusy(false) }
  }

  return (
    <>
      <h1><Tr k="Units" /></h1>
      <Link className="button" to="/items">{trLine('Back to items')}</Link>
      <ErrorText error={error} />
      {loading && !data ? <Loading /> : (
        <ul className="rows">
          {data?.filter((u) => !u.deleted).map((u) => (
            <li key={u.id} className="row static">
              <span className="row-main">{u.code} — <Name value={u.name} inline /></span>
              {!u.builtIn && can('ITEM_DELETE') && <DeleteButton busy={busy} onConfirm={() => remove(u.id, u.version)} />}
            </li>
          ))}
        </ul>
      )}
      {canAdd && (
        <form onSubmit={add} noValidate className="card">
          <h2><Tr k="Add unit" /></h2>
          <TextField label="Short code" value={code} onChange={setCode} error={fieldMessage(formError, 'code')} />
          <BilingualNameFields value={name} onChange={setName} title="Unit name" required error={fieldMessage(formError, 'name')} />
          <ErrorText error={hasFieldErrors(formError) ? null : formError} />
          <button type="submit" className="primary" disabled={busy}>{trLine('Save')}</button>
        </form>
      )}
    </>
  )
}
