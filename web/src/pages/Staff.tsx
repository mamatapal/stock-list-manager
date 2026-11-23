import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { staff, type CreatedStaff, type StaffMember, type StaffSession } from '../api/owner'
import { useAuth } from '../auth/AuthContext'
import { ErrorText } from '../components/ErrorText'
import { StepUpCancelled, useStepUp } from '../components/StepUp'
import { AddLink, Empty, Loading, TextField } from '../components/ui'
import { useLoad } from '../components/useLoad'
import { GROUPS, PERMISSIONS, STAFF_DEFAULTS } from '../permissions'
import { credentialsMessage, shareCredentials } from '../shareCredentials'
import { dateTime } from '../pdf/format'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

const when = (iso: string | null) => (iso ? dateTime(new Date(iso)) : '-')
const MOBILE = /^[6-9]\d{9}$/
const USERNAME = /^[A-Za-z0-9._]{3,40}$/

/** Checkbox list grouped by area. [grantable] limits what the signed-in user may change; the server enforces the same rule. */
export function PermissionEditor({ selected, grantable, disabled, onToggle }: { selected: Set<string>; grantable: Set<string>; disabled: boolean; onToggle: (code: string, on: boolean) => void }) {
  useLanguage()
  return (
    <div>
      {GROUPS.map((group) => (
        <fieldset key={group} className="perm-group">
          <legend><Tr k={group} inline /></legend>
          {group === 'Sensitive' && <p className="muted small"><Tr k="Off by default. Grant only to people you trust with money and accounts." /></p>}
          {PERMISSIONS.filter((p) => p.group === group).map((p) => (
            <label key={p.code} className="perm-row">
              <input type="checkbox" checked={selected.has(p.code)} disabled={disabled || !grantable.has(p.code)} onChange={(e) => onToggle(p.code, e.target.checked)} />
              <span><Tr k={p.label} inline /></span>
            </label>
          ))}
        </fieldset>
      ))}
    </div>
  )
}

function useGrantable(): Set<string> {
  const { user, can } = useAuth()
  return useMemo(() => new Set(PERMISSIONS.filter((p) => user?.role === 'OWNER' || can(p.code)).map((p) => p.code)), [user, can])
}

export function StaffList() {
  useLanguage()
  const { data, error, loading } = useLoad(() => staff.list(), [])
  return (
    <>
      <h1><Tr k="Staff" /></h1>
      <AddLink to="/staff/new" label="Add staff" />
      <ErrorText error={error} />
      {loading && !data ? <Loading /> : data && data.length === 0 ? <Empty text="No staff accounts yet. Add one so a staff member can sign in on their own phone." /> : (
        <ul className="rows">
          {data?.map((s) => (
            <li key={s.id}>
              <Link to={`/staff/${s.id}`} className="row">
                <span className="row-main">{s.fullName}</span>
                <span className="row-sub"><Tr k="@%1$s · last sign-in %2$s" args={[s.username, when(s.lastLoginAt)]} inline /></span>
                <span className={s.status === 'ACTIVE' ? 'tag' : 'tag bad'}><Tr k={s.status === 'ACTIVE' ? 'Active' : 'Inactive'} inline /></span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

function Credentials({ business, fullName, username, password }: { business: string; fullName: string; username: string; password: string }) {
  useLanguage()
  const [note, setNote] = useState<string | null>(null)
  async function share() {
    const r = await shareCredentials(credentialsMessage(business, fullName, username, password))
    setNote(r === 'copied' ? 'Copied to the clipboard' : r === 'failed' ? 'Could not share. Copy the details by hand.' : null)
  }
  return (
    <>
      <p><Tr k="Username: %1$s" args={[username]} /></p>
      <p><Tr k="Temporary password: %1$s" args={[password]} /></p>
      <button type="button" className="primary" onClick={() => { void share() }}>{trLine('Share sign-in details')}</button>
      {note && <p className="muted"><Tr k={note} /></p>}
    </>
  )
}

export function StaffCreate() {
  useLanguage()
  const { user } = useAuth()
  const run = useStepUp()
  const navigate = useNavigate()
  const grantable = useGrantable()
  const [fullName, setFullName] = useState('')
  const [username, setUsername] = useState('')
  const [mobile, setMobile] = useState('')
  const [selected, setSelected] = useState(() => new Set([...STAFF_DEFAULTS].filter((c) => grantable.has(c))))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState<CreatedStaff | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const bad: Record<string, string> = {}
    if (!fullName.trim()) bad.fullName = trLine('%1$s is required', trLine('Full name'))
    if (!username.trim()) bad.username = trLine('Username is required')
    else if (!USERNAME.test(username.trim())) bad.username = trLine('3-40 letters, digits, dots or underscores')
    if (mobile && !MOBILE.test(mobile)) bad.mobile = trLine('Enter a 10-digit mobile number')
    setErrors(bad)
    if (Object.keys(bad).length) return
    setBusy(true); setError(null)
    try {
      setCreated(await run(() => staff.create({ fullName: fullName.trim(), username: username.trim(), mobile: mobile || null, permissions: [...selected] })))
    } catch (err) {
      if (err instanceof StepUpCancelled) return
      setError(err)
      const fields = (err as { fieldErrors?: Array<{ field: string; message: string }> }).fieldErrors ?? []
      if (fields.length) setErrors(Object.fromEntries(fields.map((f) => [f.field, f.message])))
    } finally { setBusy(false) }
  }

  if (created) {
    return (
      <>
        <h1><Tr k="Account created" /></h1>
        <section className="card">
          <p><Tr k="Give these sign-in details to %1$s. The temporary password is shown only once; they must choose their own password at first sign-in." args={[created.staff.fullName]} /></p>
          <Credentials business={user?.businessName ?? ''} fullName={created.staff.fullName} username={created.staff.username} password={created.temporaryPassword} />
        </section>
        <button type="button" onClick={() => navigate('/staff', { replace: true })}>{trLine('Done')}</button>
      </>
    )
  }
  return (
    <form onSubmit={(e) => { void submit(e) }}>
      <h1><Tr k="Add staff" /></h1>
      <ErrorText error={error} />
      <TextField label="Full name" value={fullName} onChange={setFullName} error={errors.fullName} required />
      <TextField label="Username" value={username} onChange={(v) => setUsername(v.trim())} error={errors.username} required />
      <TextField label="Mobile (optional)" type="tel" inputMode="tel" value={mobile} onChange={(v) => setMobile(v.replace(/\D/g, '').slice(0, 10))} error={errors.mobile} />
      <h2><Tr k="Permissions" /></h2>
      <p className="muted small"><Tr k="Pre-selected: normal purchasing and receiving work." /></p>
      <PermissionEditor selected={selected} grantable={grantable} disabled={busy} onToggle={(c, on) => setSelected((s) => { const n = new Set(s); if (on) n.add(c); else n.delete(c); return n })} />
      <button type="submit" className="primary" disabled={busy}>{trLine(busy ? 'Creating...' : 'Create')}</button>
      <Link className="button" to="/staff">{trLine('Cancel')}</Link>
    </form>
  )
}

export function StaffDetail() {
  const { id = '' } = useParams()
  const s = useLoad(() => staff.get(id), [id])
  const p = useLoad(() => staff.permissions(id), [id])
  const sess = useLoad(() => staff.sessions(id).catch(() => [] as StaffSession[]), [id])
  if (s.loading || p.loading) return <Loading />
  if (s.error || p.error) return <ErrorText error={s.error ?? p.error} />
  return <StaffDetailBody key={id + (s.data?.fullName ?? '')} initial={s.data!} savedPermissions={p.data!.permissions} sessions={sess.data ?? []} />
}

function StaffDetailBody({ initial, savedPermissions, sessions: initialSessions }: { initial: StaffMember; savedPermissions: string[]; sessions: StaffSession[] }) {
  useLanguage()
  const { user } = useAuth()
  const run = useStepUp()
  const grantable = useGrantable()
  const [member, setMember] = useState(initial)
  const [saved, setSaved] = useState(() => new Set(savedPermissions))
  const [perms, setPerms] = useState(() => new Set(savedPermissions))
  const [sessions, setSessions] = useState(initialSessions)
  const [editing, setEditing] = useState(false)
  const [fullName, setFullName] = useState(initial.fullName)
  const [mobile, setMobile] = useState(initial.mobile ?? '')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<string | null>(null)
  const [temp, setTemp] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const active = member.status === 'ACTIVE'
  const changed = [...perms].sort().join() !== [...saved].sort().join()

  async function act<T>(call: () => Promise<T>, ok: (r: T) => void) {
    setBusy(true); setError(null); setMessage(null)
    try { ok(await run(call)) } catch (e) { if (!(e instanceof StepUpCancelled)) setError(e) } finally { setBusy(false); setConfirm(null) }
  }
  const savePermissions = () => act(() => staff.setPermissions(member.id, [...perms]), (r) => { setSaved(new Set(r.permissions)); setPerms(new Set(r.permissions)); setMessage('Permissions saved. They apply on %1$s\'s phone straight away.') })
  async function saveAll() {
    if (editing) {
      if (!fullName.trim()) { setFieldError(trLine('%1$s is required', trLine('Full name'))); return }
      if (mobile && !MOBILE.test(mobile)) { setFieldError(trLine('Enter a 10-digit mobile number')); return }
      setFieldError(null)
      await act(() => staff.update(member.id, { fullName: fullName.trim(), mobile: mobile || null }), (r) => { setMember(r); setEditing(false); setMessage('Details saved') })
    }
    if (changed) await savePermissions()
  }

  return (
    <>
      <h1>{member.fullName}</h1>
      <ErrorText error={error} />
      {message && <p className="ok" role="status"><Tr k={message} args={[member.fullName]} /></p>}
      <section className="card">
        <h2><Tr k="Details" /></h2>
        {editing ? (
          <>
            <TextField label="Full name" value={fullName} onChange={setFullName} />
            <TextField label="Mobile (optional)" type="tel" inputMode="tel" value={mobile} onChange={(v) => setMobile(v.replace(/\D/g, '').slice(0, 10))} />
            {fieldError && <p className="error" role="alert">{fieldError}</p>}
            <button type="button" onClick={() => setEditing(false)}>{trLine('Cancel')}</button>
          </>
        ) : (
          <>
            <dl>
              <dt><Tr k="Username" /></dt><dd>{member.username}</dd>
              <dt><Tr k="Mobile" /></dt><dd>{member.mobile ?? '-'}</dd>
              <dt><Tr k="Status" /></dt><dd><Tr k={active ? 'Active' : 'Inactive'} inline /></dd>
              <dt><Tr k="Created" /></dt><dd>{when(member.createdAt)}</dd>
              <dt><Tr k="Last sign-in" /></dt><dd>{when(member.lastLoginAt)}</dd>
            </dl>
            {member.mustChangePassword && <p className="muted small"><Tr k="Has not yet chosen their own password." /></p>}
            <button type="button" disabled={busy} onClick={() => { setFullName(member.fullName); setMobile(member.mobile ?? ''); setEditing(true) }}>{trLine('Edit details')}</button>
          </>
        )}
      </section>
      <section className="card">
        <h2><Tr k="Access" /></h2>
        <button type="button" disabled={busy} onClick={() => setConfirm(active ? 'deactivate' : 'activate')}>{trLine(active ? 'Deactivate account' : 'Activate account')}</button>
        <button type="button" disabled={busy} onClick={() => setConfirm('reset')}>{trLine('Reset password')}</button>
        {confirm && (
          <div className="card confirm" role="alertdialog">
            {confirm === 'deactivate' && <><h3><Tr k="Deactivate account?" /></h3><p><Tr k="%1$s will be signed out on every phone and cannot sign in until you activate the account again. Their past work stays in the records." args={[member.fullName]} /></p></>}
            {confirm === 'activate' && <><h3><Tr k="Activate account?" /></h3><p><Tr k="%1$s will be able to sign in again." args={[member.fullName]} /></p></>}
            {confirm === 'reset' && <><h3><Tr k="Reset password?" /></h3><p><Tr k="A new temporary password will be created and %1$s will be signed out on every phone." args={[member.fullName]} /></p></>}
            {confirm === 'signout' && <><h3><Tr k="Sign out on all phones?" /></h3><p><Tr k="They can sign in again with their password." /></p></>}
            <button type="button" className="danger" disabled={busy} onClick={() => {
              if (confirm === 'deactivate') void act(() => staff.setActive(member.id, false), (r) => { setMember(r); setSessions([]); setMessage('Account deactivated and signed out on all phones') })
              else if (confirm === 'activate') void act(() => staff.setActive(member.id, true), (r) => { setMember(r); setMessage('Account activated') })
              else if (confirm === 'reset') void act(() => staff.resetPassword(member.id), (r) => { setTemp(r.temporaryPassword); setSessions([]) })
              else void act(() => staff.revokeSessions(member.id), () => { setSessions([]); setMessage('Signed out on all phones') })
            }}>{trLine('Yes')}</button>
            <button type="button" onClick={() => setConfirm(null)}>{trLine('Cancel')}</button>
          </div>
        )}
        {temp && (
          <div className="card" role="dialog">
            <h3><Tr k="New temporary password" /></h3>
            <p><Tr k="Give this to %1$s. It is shown only once:" args={[member.fullName]} /></p>
            <Credentials business={user?.businessName ?? ''} fullName={member.fullName} username={member.username} password={temp} />
            <button type="button" onClick={() => setTemp(null)}>{trLine('Done')}</button>
          </div>
        )}
      </section>
      <section className="card">
        <h2><Tr k="Permissions" /></h2>
        {changed && <p className="muted small"><Tr k="Unsaved changes - tap Save." /></p>}
        <PermissionEditor selected={perms} grantable={grantable} disabled={busy} onToggle={(c, on) => setPerms((s) => { const n = new Set(s); if (on) n.add(c); else n.delete(c); return n })} />
      </section>
      <section className="card">
        <h2><Tr k="Signed-in phones" /></h2>
        {sessions.length === 0 ? <p className="muted"><Tr k="Not signed in on any phone." /></p> : (
          <>
            {sessions.map((x) => <p key={x.familyId}>{x.deviceName ?? trLine('Phone')} · <Tr k="since %1$s" args={[when(x.signedInAt)]} inline /></p>)}
            <button type="button" disabled={busy} onClick={() => setConfirm('signout')}>{trLine('Sign out on all phones')}</button>
          </>
        )}
      </section>
      <button type="button" className="primary sticky-save" disabled={busy || !(editing || changed)} onClick={() => { void saveAll() }}>{trLine(busy ? 'Saving...' : 'Save')}</button>
    </>
  )
}
