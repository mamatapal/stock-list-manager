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
  if (typeof error === 'string') return trMessage(error)
  return trBoth('Something went wrong. Please try again.')
}
