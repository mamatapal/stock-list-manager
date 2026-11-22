import { trBoth, useLanguage } from './lang'

type Args = ReadonlyArray<string | number | null | undefined>

/** English text shown in both languages: main language first, the other one smaller underneath (or after a slash). */
export function Tr({ k, args = [], inline = false }: { k: string; args?: Args; inline?: boolean }) {
  useLanguage()
  const [main, other] = trBoth(k, ...args)
  return <Pair main={main} other={other} inline={inline} />
}

export function Pair({ main, other, inline = false }: { main: string; other: string | null; inline?: boolean }) {
  if (!other) return <span className="bi">{main}</span>
  if (inline) return <span className="bi">{main} / {other}</span>
  return (
    <span className="bi">
      <span className="bi-main">{main}</span>
      <span className="bi-other">{other}</span>
    </span>
  )
}
