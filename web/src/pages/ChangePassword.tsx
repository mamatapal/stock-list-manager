import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { changePassword } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { ErrorText } from '../components/ErrorText'
import { PasswordField } from '../components/PasswordField'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

export function ChangePassword() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  useLanguage()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [again, setAgain] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const forced = !!user?.mustChangePassword

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (next.length < 8) { setError('New password must be at least 8 characters'); return }
    if (next !== again) { setError('Passwords do not match'); return }
    setBusy(true); setError(null)
    try { await changePassword(current, next); navigate('/', { replace: true }) } catch (err) { setError(err) } finally { setBusy(false) }
  }

  return (
    <div className={forced ? 'login' : undefined}>
      <h1><Tr k="Change password" /></h1>
      {forced && <p className="hint"><Tr k="You must set a new password before you continue." /></p>}
      <form onSubmit={submit} noValidate>
        <PasswordField label="Current password" value={current} onChange={setCurrent} autoComplete="current-password" />
        <PasswordField label="New password" value={next} onChange={setNext} autoComplete="new-password" />
        <PasswordField label="Confirm new password" value={again} onChange={setAgain} autoComplete="new-password" />
        <ErrorText error={error} />
        <button type="submit" className="primary" disabled={busy}>{busy ? trLine('Saving…') : trLine('Save')}</button>
      </form>
      {forced
        ? <button type="button" className="danger" onClick={() => { void signOut() }}>{trLine('Sign out')}</button>
        : <Link className="button" to="/account">{trLine('Cancel')}</Link>}
    </div>
  )
}
