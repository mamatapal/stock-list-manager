import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { takeEndedMessage } from '../auth/session'
import { useAuth } from '../auth/AuthContext'
import { ErrorText } from '../components/ErrorText'
import { PasswordField } from '../components/PasswordField'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

export function Login() {
  const { status, signIn } = useAuth()
  useLanguage()
  const [role, setRole] = useState<'OWNER' | 'STAFF'>('OWNER')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(() => takeEndedMessage())
  const [busy, setBusy] = useState(false)
  if (status === 'in') return <Navigate to="/" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) { setError('Enter your username and password'); return }
    setBusy(true); setError(null)
    try { await signIn(username, password, role) } catch (err) { setError(err) } finally { setBusy(false) }
  }

  return (
    <div className="login">
      <div className="login-top"><LanguageSwitch compact /></div>
      <h1>Stock List Manager</h1>
      <div className="segmented" role="group">
        <button type="button" className={role === 'OWNER' ? 'seg on' : 'seg'} aria-pressed={role === 'OWNER'} onClick={() => setRole('OWNER')}>
          <Tr k="Owner sign-in" />
        </button>
        <button type="button" className={role === 'STAFF' ? 'seg on' : 'seg'} aria-pressed={role === 'STAFF'} onClick={() => setRole('STAFF')}>
          <Tr k="Staff sign-in" />
        </button>
      </div>
      <p className="hint">
        <Tr k={role === 'OWNER'
          ? 'Sign in with the username you chose when registering the business.'
          : 'Sign in with the username and password your shop owner gave you.'} />
      </p>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="username"><Tr k="Username" /></label>
          <input id="username" value={username} autoComplete="username" autoCapitalize="none" autoCorrect="off"
            spellCheck={false} onChange={(e) => setUsername(e.target.value)} />
        </div>
        <PasswordField label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
        <ErrorText error={error} />
        <button type="submit" className="primary" disabled={busy}>
          {busy ? trLine('Signing in…') : trLine('Sign in')}
        </button>
      </form>
      {role === 'OWNER' && <p className="hint small"><Tr k="Register a new business on the Android app first, then sign in here." /></p>}
    </div>
  )
}
