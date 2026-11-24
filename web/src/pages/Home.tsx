import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { NAV, visibleNav } from '../components/nav'
import { Tr } from '../i18n/Tr'
import { useLanguage } from '../i18n/lang'

export function Home() {
  const { user, can } = useAuth()
  useLanguage()
  if (!user) return null
  const cards = visibleNav(NAV, can, user?.role === 'OWNER')
  return (
    <>
      <h1><Tr k="Hello, %1$s" args={[user.fullName]} /></h1>
      <p className="muted"><Tr k={user.role === 'OWNER' ? 'Owner · full access' : 'Staff'} /></p>
      <div className="cards">
        {cards.map((c) => (
          <Link key={c.to} to={c.to} className="dcard">
            <span className="dcard-icon" aria-hidden="true">{c.icon}</span>
            <span className="dcard-text">
              <span className="dcard-title"><Tr k={c.label} /></span>
              <span className="dcard-sub"><Tr k={c.subtitle} /></span>
            </span>
            <span className="dcard-go" aria-hidden="true">›</span>
          </Link>
        ))}
      </div>
      {cards.length === 0 && <p className="muted"><Tr k="Your owner has not given you any permissions yet." /></p>}
      <p className="hint small"><Tr k="Staff, reports and business profile are being added to the web app step by step." /></p>
    </>
  )
}
