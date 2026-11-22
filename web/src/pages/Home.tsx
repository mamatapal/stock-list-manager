import { useAuth } from '../auth/AuthContext'
import { Tr } from '../i18n/Tr'

export function Home() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <>
      <h1><Tr k="Welcome" /></h1>
      <section className="card">
        <p className="big">{user.fullName}</p>
        <p>{user.businessName}</p>
        <p className="muted"><Tr k={user.role === 'OWNER' ? 'Owner' : 'Staff'} /></p>
      </section>
      <section className="card">
        <p><Tr k="The web app works the same way as the Android app, but only while you are online. More screens are being added step by step." /></p>
      </section>
    </>
  )
}
