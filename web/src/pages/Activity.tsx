import { useState } from 'react'
import { activity, type ActivityEntry } from '../api/owner'
import { ErrorText } from '../components/ErrorText'
import { Empty, Loading } from '../components/ui'
import { useLoad } from '../components/useLoad'
import { actionLabel } from '../permissions'
import { dateTime } from '../pdf/format'
import { Tr } from '../i18n/Tr'
import { trLine, useLanguage } from '../i18n/lang'

/** Owner only: who did what, newest first. Read-only; nothing here can change or delete an entry. */
export function ActivityPage() {
  useLanguage()
  const [pages, setPages] = useState<ActivityEntry[][]>([])
  const [page, setPage] = useState(0)
  const [more, setMore] = useState(false)
  const [moreBusy, setMoreBusy] = useState(false)
  const [moreError, setMoreError] = useState<unknown>(null)
  const first = useLoad(() => activity.page(0).then((r) => { setPages([r.items]); setMore(r.items.length < r.total); return r }), [])

  async function loadMore() {
    setMoreBusy(true); setMoreError(null)
    try {
      const r = await activity.page(page + 1)
      const all = [...pages, r.items]
      setPages(all); setPage(page + 1)
      setMore(all.flat().length < r.total)
    } catch (e) { setMoreError(e) } finally { setMoreBusy(false) }
  }
  const items = pages.flat()
  return (
    <>
      <h1><Tr k="Activity log" /></h1>
      <ErrorText error={first.error ?? moreError} />
      {first.loading ? <Loading /> : items.length === 0 ? <Empty text="No activity yet." /> : (
        <ul className="rows">
          {items.map((e) => {
            const a = actionLabel(e.action)
            return (
              <li key={e.id} className="row static">
                <span className="row-main">{a.known ? <Tr k={a.english} inline /> : a.english}</span>
                <span className="row-sub">{e.username ?? trLine('Unknown user')} · {dateTime(new Date(e.createdAt))}</span>
              </li>
            )
          })}
        </ul>
      )}
      {more && <button type="button" disabled={moreBusy} onClick={() => { void loadMore() }}>{trLine(moreBusy ? 'Loading...' : 'Load more')}</button>}
    </>
  )
}
