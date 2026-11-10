import { useEffect, useState } from "react"
import { Lightbox } from "../../components/Lightbox"
import { ConfirmDialog, Pager, StaffHeader, StaffState, StatusLine } from "../../components/StaffUi"
import { limits } from "../../lib/limits"
import { json, staffApi, useStaff } from "../../lib/staff"
import { ordinal, whenAt } from "../../lib/staffText"
import type { Message, Page } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

/** How to answer: an email link for an address, a phone link for a number. */
function replyLink(contact: string) {
  if (contact.includes("@")) return { href: `mailto:${contact}`, label: "Reply by email" }
  const digits = contact.replace(/[^+\d]/g, "")
  return digits.length >= 6 ? { href: `tel:${digits}`, label: "Call" } : null
}

/** Messages: what people wrote with the contact form, until the studio has handled it. */
export default function AdminMessages() {
  usePageTitle("Messages")
  const staff = useStaff()
  const [read, setRead] = useState("false")
  const [typed, setTyped] = useState("")
  const [q, setQ] = useState("")
  const [page, setPage] = useState(0)
  const [deleting, setDeleting] = useState<Message | null>(null)
  const [viewing, setViewing] = useState<{ message: Message; index: number } | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")
  useEffect(() => {
    const timer = window.setTimeout(() => setQ(typed.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [typed])
  const messages = useLoad<Page<Message>>(
    `/admin/messages?page=${page}&size=20&sort=date,desc${read ? `&read=${read}` : ""}${q ? `&q=${encodeURIComponent(q)}` : ""}`,
    staff?.token,
  )

  const say = (text: string) => {
    setNote(text)
    setProblem("")
    messages.reload()
  }

  async function handled(m: Message, value: boolean) {
    try {
      await staffApi(`/admin/messages/${m.id}`, json("PATCH", { read: value }))
      say(value ? `${m.name}'s message is marked as handled.` : `${m.name}'s message is back with the waiting ones.`)
    } catch (reason) {
      setNote("")
      setProblem(errorText(reason))
    }
  }

  const items = messages.data?.items ?? []
  return (
    <>
      <StaffHeader title="Messages">Contact form messages waiting for the studio.</StaffHeader>
      <StatusLine>{note}</StatusLine>
      <StatusLine error>{problem}</StatusLine>
      <div className="admin-filters">
        <label>
          Show
          <select value={read} onChange={(e) => { setRead(e.target.value); setPage(0) }}>
            <option value="false">Waiting</option>
            <option value="true">Handled</option>
            <option value="">All messages</option>
          </select>
        </label>
        <label>
          Search
          <input type="search" value={typed} maxLength={limits["search.q"]} placeholder="Name, contact or text"
            onChange={(e) => { setTyped(e.target.value); setPage(0) }} />
        </label>
      </div>
      <StaffState loading={messages.loading && !messages.data} error={messages.error} onRetry={messages.reload}
        empty={messages.data && items.length === 0 && (read === "false" && !q ? "Nothing waiting. New messages appear here." : "No messages match.")} />
      <div className="admin-message-list">
        {items.map((m) => {
          const reply = replyLink(m.contact)
          return (
            <article key={m.id} className={`slip on-paper${m.read ? " is-handled" : ""}`}>
              <header>
                <strong>
                  {m.name}, {m.contact}
                </strong>
                <time dateTime={m.createdAt}>{whenAt(m.createdAt)}</time>
              </header>
              {m.senderMessageNumber > 1 && <p className="message-note">Their {ordinal(m.senderMessageNumber)} message to the studio.</p>}
              <p>{m.text}</p>
              {m.photos.length > 0 && (
                <div className="attachments">
                  {m.photos.map((photo, i) => (
                    <button key={photo.id} type="button" aria-label={`Open photo ${i + 1}`} onClick={() => setViewing({ message: m, index: i })}>
                      <img src={photo.thumbUrl} alt="" />
                    </button>
                  ))}
                </div>
              )}
              <div className="message-actions">
                <button className="button button-secondary staff-compact" type="button" onClick={() => handled(m, !m.read)}>
                  {m.read ? "Mark as waiting" : "Mark as handled"}
                </button>
                {reply && (
                  <a className="staff-text-button" href={reply.href}>
                    {reply.label}
                  </a>
                )}
                <button className="staff-text-button" type="button" onClick={() => setDeleting(m)}>Delete</button>
                {m.read && m.readAt && <span className="message-handled">Handled: {whenAt(m.readAt)}</span>}
              </div>
            </article>
          )
        })}
      </div>
      {messages.data && <Pager page={messages.data.page} totalPages={messages.data.totalPages} onPage={setPage} />}
      <p className="staff-note">Messages and their photos are deleted automatically after 90 days.</p>

      {deleting && (
        <ConfirmDialog
          title="Delete this message?"
          confirmLabel="Delete message"
          onClose={() => setDeleting(null)}
          onConfirm={async () => {
            await staffApi(`/admin/messages/${deleting.id}`, { method: "DELETE" })
            setDeleting(null)
            say("Message deleted.")
          }}
        >
          {deleting.name}'s message and its photos are removed for good.
        </ConfirmDialog>
      )}
      {viewing && (
        <Lightbox
          photos={viewing.message.photos.map((image, i) => ({ image, caption: `Photo ${i + 1} from ${viewing.message.name}` }))}
          index={viewing.index}
          label={`Photos from ${viewing.message.name}`}
          onChange={(index) => setViewing({ ...viewing, index })}
          onClose={() => setViewing(null)}
        />
      )}
    </>
  )
}
