/**
 * Sensitive owner actions (staff, permissions, business details) need a short-lived "step-up" token, got by typing the
 * password again. It is kept in memory only (never stored) and sent with every change until it expires.
 */
let token: string | null = null
let expires = 0

export function setStepUp(t: string, expiresAt: string): void {
  token = t
  // Drop it a little early so a request never carries a token that expires on the way.
  expires = Date.parse(expiresAt) - 5_000
}
export function clearStepUp(): void { token = null; expires = 0 }
export function stepUpToken(): string | null {
  if (token && Date.now() < expires) return token
  token = null
  return null
}
