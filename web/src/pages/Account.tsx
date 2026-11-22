import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

export function Account() {
  const { user, signOut } = useAuth()
  useLanguage()
  if (!user) return null
  return (
    <>
      <h1><Tr k="Account" /></h1>
      <section className="card">
        <dl>
          <dt><Tr k="Name" /></dt><dd>{user.fullName}</dd>
          <dt><Tr k="Username" /></dt><dd>{user.username}</dd>
          <dt><Tr k="Business" /></dt><dd>{user.businessName}</dd>
          <dt><Tr k="Role" /></dt><dd><Tr k={user.role === 'OWNER' ? 'Owner' : 'Staff'} /></dd>
          <dt><Tr k="App version" /></dt><dd>{__APP_VERSION__}</dd>
        </dl>
      </section>
      <section className="card">
        <h2><Tr k="Main language" /></h2>
        <p className="hint"><Tr k="Choose which language is shown first. Both languages are always shown." /></p>
        <LanguageSwitch />
      </section>
      <Link className="button" to="/change-password">{trLine('Change password')}</Link>
      <button type="button" className="danger" onClick={() => { void signOut() }}>{trLine('Sign out')}</button>
    </>
  )
}
