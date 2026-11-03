import { ListToolbar, Pager } from "../../components/ListControls"
import { useState, type FormEvent } from "react"
import { Button, Checkbox, Eyebrow, Heading, Input, Select, Textarea } from "../../components/ui"
import { telHref } from "../../lib/data"
import { useListQuery, type Page } from "../../lib/paging"
import type { AdminReview, Message } from "../../lib/types"
import {
  formatDateTime,
  send,
  useAction,
  useStaffLoad,
} from "../../features/staff/staffUtils"
import { Feedback, Loading } from "../../features/staff/StaffUi"
import { limits } from "../../lib/limits"

/** Admin: review moderation (/admin/reviews) and contact messages (/admin/messages). */
export function AdminReviews() {
  const list = useListQuery({ sort: "date,desc", filters: { status: "", minRating: "" } })
  const load = useStaffLoad<Page<AdminReview>>(`/admin/reviews?${list.query}`)
  const pending = useStaffLoad<Page<AdminReview>>("/admin/reviews?status=pending&size=1")
  const items = load.data?.items
  const action = useAction()
  const change = (item: AdminReview, approved: boolean | null) => {
    if (approved === null && !window.confirm(`Delete the review by ${item.name}?`))
      return
    action
      .run(item.id, () =>
        approved === null
          ? send(`/admin/reviews/${item.id}`, "DELETE")
          : send(`/admin/reviews/${item.id}`, "PATCH", { approved }),
      )
      .then((ok) => {
        if (!ok) return
        load.reload()
        pending.reload()
      })
  }
  const waiting = pending.data?.totalItems ?? 0
  const [editing, setEditing] = useState<AdminReview | null>(null)
  const [draft, setDraft] = useState({ name: "", rating: 5, text: "", approved: false })
  async function saveEdit(event: FormEvent) {
    event.preventDefault()
    if (!editing) return
    const ok = await action.run("save", () => send(`/admin/reviews/${editing.id}`, "PUT", draft), "Review saved.")
    if (ok) {
      setEditing(null)
      load.reload()
      pending.reload()
    }
  }
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Moderation{waiting ? ` · ${waiting} waiting` : ""}</Eyebrow>
          <Heading level={1}>Reviews.</Heading>
        </div>
      </div>
      {editing ? (
        <form className="details-form admin-form" onSubmit={saveEdit}>
          <Heading level={3}>Edit review</Heading>
          <Input
            label="Name"
            required
            maxLength={limits["review.name"]}
            value={draft.name}
            error={action.fieldErrors.name}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
          <Select
            label="Stars"
            value={draft.rating}
            onChange={(e) => setDraft({ ...draft, rating: Number(e.target.value) })}
          >
            {[5, 4, 3, 2, 1].map((stars) => (
              <option key={stars} value={stars}>
                {stars}
              </option>
            ))}
          </Select>
          <Textarea
            label="Review"
            required
            maxLength={limits["review.text"]}
            rows={4}
            value={draft.text}
            error={action.fieldErrors.text}
            onChange={(e) => setDraft({ ...draft, text: e.target.value })}
          />
          <Checkbox
            className="policy-check"
            label="Show on the site"
            checked={draft.approved}
            onChange={(e) => setDraft({ ...draft, approved: e.target.checked })}
          />
          <div className="row-actions">
            <Button type="submit" loading={action.busy === "save"}>
              Save review
            </Button>
            <Button type="button" variant="ghost" onClick={() => setEditing(null)}>
              Cancel edit
            </Button>
          </div>
        </form>
      ) : null}
      <ListToolbar
        list={list}
        searchLabel="Search reviews"
        placeholder="Name or text…"
        sorts={[
          ["date,desc", "Newest first"],
          ["date,asc", "Oldest first"],
          ["rating,desc", "Most stars"],
          ["rating,asc", "Fewest stars"],
          ["name,asc", "Name A–Z"],
        ]}
      >
        <Select
          label="Status"
          value={list.filters.status}
          onChange={(e) => list.setFilter("status", e.target.value)}
        >
          <option value="">All</option>
          <option value="pending">Waiting for approval</option>
          <option value="approved">On the site</option>
        </Select>
        <Select
          label="Stars"
          value={list.filters.minRating}
          onChange={(e) => list.setFilter("minRating", e.target.value)}
        >
          <option value="">Any</option>
          {[5, 4, 3, 2].map((stars) => (
            <option key={stars} value={stars}>
              {stars === 5 ? "5 only" : `${stars} or more`}
            </option>
          ))}
        </Select>
      </ListToolbar>
      <Feedback error={action.error} />
      {!items ? (
        <Loading error={load.error} />
      ) : items.length ? (
        <div className="booking-table">
          {items.map((item) => (
            <article key={item.id}>
              <div>
                <Heading level={3}>
                  {item.name} · {item.rating}/5
                </Heading>
                <p>{item.text}</p>
                <p>
                  <small>{formatDateTime(item.createdAt)}</small>
                </p>
              </div>
              <span className={`status ${item.approved ? "status-done" : "status-awaiting"}`}>
                {item.approved ? "On the site" : "Waiting"}
              </span>
              <div className="row-actions">
                <Button
                  variant="secondary"
                  loading={action.busy === item.id}
                  onClick={() => change(item, !item.approved)}
                >
                  {item.approved ? "Hide" : "Approve"}
                </Button>
                <Button
                  variant="ghost"
                  onClick={() => {
                    setEditing(item)
                    setDraft({ name: item.name, rating: item.rating, text: item.text, approved: item.approved })
                    action.clear()
                    window.scrollTo({ top: 0, behavior: "smooth" })
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="danger"
                  disabled={action.busy === item.id}
                  onClick={() => change(item, null)}
                >
                  Delete
                </Button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <p>No reviews match this search.</p>
        </div>
      )}
      <Pager data={load.data} list={list} noun="review" />
    </div>
  )
}

/** 2 → "2nd", 3 → "3rd", 11 → "11th" */
const ordinal = (n: number) =>
  n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : ["th", "st", "nd", "rd"][n % 10] ?? "th")

export function AdminMessages() {
  const list = useListQuery({ sort: "date,desc", filters: { read: "" } })
  const load = useStaffLoad<Page<Message>>(`/admin/messages?${list.query}`)
  const unreadLoad = useStaffLoad<Page<Message>>("/admin/messages?read=false&size=1")
  const items = load.data?.items
  const unread = unreadLoad.data?.totalItems ?? 0
  const action = useAction()
  const run = (item: Message, remove: boolean) => {
    if (remove && !window.confirm(`Delete the message from ${item.name}?`)) return
    action
      .run(item.id, () =>
        remove
          ? send(`/admin/messages/${item.id}`, "DELETE")
          : send(`/admin/messages/${item.id}`, "PATCH", { read: !item.read }),
      )
      .then((ok) => {
        if (!ok) return
        load.reload()
        unreadLoad.reload()
      })
  }
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Inbox{unread ? ` · ${unread} unread` : ""}</Eyebrow>
          <Heading level={1}>Messages.</Heading>
        </div>
      </div>
      <p>Messages are deleted automatically after 90 days.</p>
      <ListToolbar
        list={list}
        searchLabel="Search messages"
        placeholder="Name, email, phone or text…"
        sorts={[
          ["date,desc", "Newest first"],
          ["date,asc", "Oldest first"],
          ["name,asc", "Name A–Z"],
        ]}
      >
        <Select
          label="Show"
          value={list.filters.read}
          onChange={(e) => list.setFilter("read", e.target.value)}
        >
          <option value="">All</option>
          <option value="false">Unread</option>
          <option value="true">Read</option>
        </Select>
      </ListToolbar>
      <Feedback error={action.error} />
      {!items ? (
        <Loading error={load.error} />
      ) : items.length ? (
        <div className="booking-table">
          {items.map((item) => (
            <article key={item.id}>
              <div>
                <Heading level={3}>
                  {item.name}
                  {item.read ? "" : " · new"}
                </Heading>
                <p>
                  <a
                    href={
                      item.contact.includes("@")
                        ? `mailto:${item.contact}`
                        : telHref(item.contact)
                    }
                  >
                    {item.contact}
                  </a>{" "}
                  · {formatDateTime(item.createdAt)}
                  {item.senderMessageNumber > 1 ? ` · ${ordinal(item.senderMessageNumber)} message from this contact` : ""}
                  {item.readAt ? ` · read ${formatDateTime(item.readAt)}` : ""}
                </p>
                <p>{item.text}</p>
              </div>
              <div className="row-actions">
                <Button
                  variant={item.read ? "ghost" : "secondary"}
                  loading={action.busy === item.id}
                  onClick={() => run(item, false)}
                >
                  {item.read ? "Mark unread" : "Mark read"}
                </Button>
                <Button
                  variant="danger"
                  disabled={action.busy === item.id}
                  onClick={() => run(item, true)}
                >
                  Delete
                </Button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <Heading level={3}>{list.q || list.filters.read ? "Nothing here." : "Inbox zero."}</Heading>
          <p>
            {list.q || list.filters.read
              ? "No messages match this search."
              : "New contact messages will land here."}
          </p>
        </div>
      )}
      <Pager data={load.data} list={list} noun="message" />
    </div>
  )
}
