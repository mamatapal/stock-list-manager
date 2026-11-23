import { translate, getLanguage, otherLanguage, type Language } from './i18n/lang'

const lines = (lang: Language, business: string, fullName: string, username: string, password: string): string => [
  translate('Hello %1$s,', lang, [fullName]), '',
  translate('Your Stock List Manager account for %1$s is ready.', lang, [business]), '',
  translate('Username: %1$s', lang, [username]),
  translate('Temporary password: %1$s', lang, [password]), '',
  translate('Open the Stock List Manager app or web page, choose "Staff sign-in" and sign in with these details.', lang),
  translate('Web page: %1$s', lang, [`${location.origin}/app/`]),
  translate('You will be asked to choose your own password straight away. Please don\'t share this message.', lang),
].join('\n')

/** The message the owner sends to a new staff member: main language first, then the other. */
export const credentialsMessage = (business: string, fullName: string, username: string, password: string): string =>
  `${lines(getLanguage(), business, fullName, username, password)}\n\n${lines(otherLanguage(), business, fullName, username, password)}`

export const credentialsSubject = (): string => `${translate('Your Stock List Manager sign-in', getLanguage())} / ${translate('Your Stock List Manager sign-in', otherLanguage())}`

/** Opens the phone's share sheet; where that is missing, copies the message. Resolves to what happened. */
export async function shareCredentials(text: string): Promise<'shared' | 'copied' | 'failed'> {
  try {
    if (navigator.share) { await navigator.share({ title: credentialsSubject(), text }); return 'shared' }
    await navigator.clipboard.writeText(text)
    return 'copied'
  } catch (e) {
    if ((e as { name?: string }).name === 'AbortError') return 'shared'
    return 'failed'
  }
}
