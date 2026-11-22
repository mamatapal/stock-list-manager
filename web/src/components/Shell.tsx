import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'
import { NAV, visibleNav } from './nav'

/** The signed-in frame: a title bar and a bottom menu that only lists what the user may open. */
export function Shell() {
  const { user, can } = useAuth()
  useLanguage()
  const items = visibleNav(NAV, can).filter((i) => i.bottom)
  return (
    <div className="shell">
      <header className="appbar">
        <strong>Stock List Manager</strong>
        <span className="appbar-sub">{user?.businessName}</span>
      </header>
      <main className="content"><Outlet /></main>
      <nav className="bottomnav" aria-label={trLine('Main navigation')}>
        {items.map((i) => (
          <NavLink key={i.to} to={i.to} end className={({ isActive }) => (isActive ? 'navlink active' : 'navlink')}>
            <Tr k={i.label} />
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
