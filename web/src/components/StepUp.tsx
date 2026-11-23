import { createContext, useCallback, useContext, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { reauth } from '../api/owner'
import { setStepUp } from '../api/stepUp'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'
import { ErrorText } from './ErrorText'
import { PasswordField } from './PasswordField'

/** Thrown when the owner closes the password box instead of confirming; screens ignore it. */
export class StepUpCancelled extends Error { constructor() { super('cancelled') } }

type Run = <T>(action: () => Promise<T>) => Promise<T>
const Ctx = createContext<Run>((a) => a())

/** Runs [action]; when the server asks for the password again, shows the box, then repeats the action once. */
export const useStepUp = (): Run => useContext(Ctx)

export function StepUpProvider({ children }: { children: ReactNode }) {
  useLanguage()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const waiting = useRef<{ ok: () => void; no: (e: Error) => void } | null>(null)

  const ask = useCallback(() => new Promise<void>((ok, no) => {
    waiting.current = { ok, no }
    setPassword(''); setError(null); setOpen(true)
  }), [])

  const run: Run = useCallback(async (action) => {
    try { return await action() } catch (e) {
      if (!(e instanceof ApiError) || e.code !== 'STEP_UP_REQUIRED') throw e
    }
    await ask()
    return action()
  }, [ask])

  async function submit(ev: FormEvent) {
    ev.preventDefault()
    if (!password) return
    setBusy(true); setError(null)
    try {
      const r = await reauth(password)
      setStepUp(r.stepUpToken, r.expiresAt)
      setOpen(false)
      waiting.current?.ok()
    } catch (e) { setError(e) } finally { setBusy(false); setPassword('') }
  }
  function cancel() { setOpen(false); waiting.current?.no(new StepUpCancelled()); waiting.current = null }

  return (
    <Ctx.Provider value={run}>
      {children}
      {open && (
        <div className="modal" role="dialog" aria-modal="true" aria-labelledby="stepup-title">
          <form className="card" onSubmit={(e) => { void submit(e) }}>
            <h2 id="stepup-title"><Tr k="Confirm it's you" /></h2>
            <p><Tr k="Enter your password to continue. You won't be asked again for 5 minutes." /></p>
            <PasswordField label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
            <ErrorText error={error} />
            <button type="submit" className="primary" disabled={busy || !password}>{trLine(busy ? 'Checking...' : 'Continue')}</button>
            <button type="button" disabled={busy} onClick={cancel}>{trLine('Cancel')}</button>
          </form>
        </div>
      )}
    </Ctx.Provider>
  )
}
