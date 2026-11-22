import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { Shell } from './components/Shell'
import { Tr } from './i18n/Tr'
import { Account } from './pages/Account'
import { ChangePassword } from './pages/ChangePassword'
import { Home } from './pages/Home'
import { Login } from './pages/Login'

/** Signed-in pages. A user who must set a new password can do nothing else until they have. */
function Protected() {
  const { status, user } = useAuth()
  if (status === 'loading') return <div className="center"><Tr k="Loading…" /></div>
  if (status === 'out') return <Navigate to="/login" replace />
  if (user?.mustChangePassword) return <Navigate to="/change-password" replace />
  return <Shell />
}

function ForcedPassword() {
  const { status, user } = useAuth()
  if (status === 'loading') return <div className="center"><Tr k="Loading…" /></div>
  if (status === 'out') return <Navigate to="/login" replace />
  return user?.mustChangePassword ? <ChangePassword /> : <Shell />
}

export function App() {
  return (
    <AuthProvider>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/change-password" element={<ForcedPassword />}>
            <Route index element={<ChangePassword />} />
          </Route>
          <Route element={<Protected />}>
            <Route index element={<Home />} />
            <Route path="account" element={<Account />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </HashRouter>
    </AuthProvider>
  )
}
