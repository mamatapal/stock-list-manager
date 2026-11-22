import { ApiError, NetworkError } from '../api/client'
import { Pair } from '../i18n/Tr'
import { trBoth, trMessage } from '../i18n/lang'

/** An error from the server or the network, in both languages. */
export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null
  const [main, other] = describe(error)
  return <div className="error" role="alert"><Pair main={main} other={other} /></div>
}

export function describe(error: unknown): [string, string | null] {
  if (error instanceof NetworkError) return trBoth('Could not reach the server. Check your internet connection.')
  if (error instanceof ApiError && error.message) return trMessage(error.message)
  if (error instanceof ApiError && error.fieldErrors.length > 0) return trMessage(error.fieldErrors[0].message)
  if (typeof error === 'string') return trMessage(error)
  return trBoth('Something went wrong. Please try again.')
}

/** The server's message for one field of a form (translated main language), or null. */
export function fieldMessage(error: unknown, field: string): string | null {
  if (!(error instanceof ApiError)) return null
  const f = error.fieldErrors.find((e) => e.field === field)
  return f ? trMessage(f.message).filter(Boolean).join(' / ') : null
}

/** Errors that belong to the whole form (not to one field). */
export function formLevelError(error: unknown): unknown {
  if (error instanceof ApiError && error.fieldErrors.length > 0 && !error.message) return null
  return error
}

export const hasFieldErrors = (error: unknown): boolean => error instanceof ApiError && error.fieldErrors.length > 0
