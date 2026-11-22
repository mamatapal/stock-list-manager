import { Link, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { trLine, useLanguage } from '../i18n/lang'

/** The signed-in frame, like the Android app: a title bar (business name, back to Home, My account) and the screen. */
export function Shell() {
  const { user } = useAuth()
  const { pathname } = useLocation()
  useLanguage()
  const home = pathname === '/'
  return (
    <div className="shell">
      <header className="appbar">
        {!home && <Link className="appbar-btn" to="/" aria-label={trLine('Home')}>‹</Link>}
        <span className="appbar-title">{user?.businessName}</span>
        {pathname !== '/account' && <Link className="appbar-btn" to="/account" aria-label={trLine('My account')}>👤</Link>}
      </header>
      <main className="content"><Outlet /></main>
    </div>
  )
}
