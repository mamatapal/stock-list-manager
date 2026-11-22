import { useAuth } from '../auth/AuthContext'
import { Link } from 'react-router-dom'
import { NAV, visibleNav } from '../components/nav'
import { Tr } from '../i18n/Tr'

export function Home() {
  const { user, can } = useAuth()
  if (!user) return null
  return (
    <>
      <h1><Tr k="Welcome" /></h1>
      <section className="card">
        <p className="big">{user.fullName}</p>
        <p>{user.businessName}</p>
        <p className="muted"><Tr k={user.role === 'OWNER' ? 'Owner' : 'Staff'} /></p>
      </section>
      <div className="tiles">
        {visibleNav(NAV, can).filter((i) => i.to !== '/' && i.to !== '/account').map((i) => (
          <Link key={i.to} to={i.to} className="tile"><Tr k={i.label} /></Link>
        ))}
      </div>
      <section className="card">
        <p><Tr k="The web app works the same way as the Android app, but only while you are online. More screens are being added step by step." /></p>
      </section>
    </>
  )
}
