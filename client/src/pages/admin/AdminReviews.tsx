import { useState, type FormEvent } from "react"
import { ConfirmDialog, Pager, StaffHeader, StaffState, StaffSwitch, StatusLine } from "../../components/StaffUi"
import { Dialog, FormError, SelectField, TextAreaField, TextField } from "../../components/Studio"
import { ApiError } from "../../lib/api"
import { limits } from "../../lib/limits"
import { json, staffApi, useStaff } from "../../lib/staff"
import { whenAt } from "../../lib/staffText"
import type { AdminReview, ArtistOption, Page } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

/** Reviews: choose which customer reviews appear on the artists' pages. */
export default function AdminReviews() {
  usePageTitle("Reviews")
  const staff = useStaff()
  const token = staff?.token
  const [status, setStatus] = useState("pending")
  const [artistId, setArtistId] = useState("")
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<AdminReview | null>(null)
  const [deleting, setDeleting] = useState<AdminReview | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")
  const reviews = useLoad<Page<AdminReview>>(
    `/admin/reviews?page=${page}&size=30&sort=date,desc${status ? `&status=${status}` : ""}${artistId ? `&artistId=${artistId}` : ""}`,
    token,
  )
  const artists = useLoad<ArtistOption[]>("/admin/artists/options", token)
  const artistName = (id: string) => artists.data?.find((a) => a.id === id)?.name ?? ""

  const say = (text: string) => {
    setNote(text)
    setProblem("")
    reviews.reload()
  }

  async function show(r: AdminReview, approved: boolean) {
    try {
      await staffApi(`/admin/reviews/${r.id}`, json("PATCH", { approved }))
      say(approved ? `${r.name}'s review is on ${artistName(r.artistId)}'s page.` : `${r.name}'s review is hidden.`)
    } catch (reason) {
      setNote("")
      setProblem(errorText(reason))
    }
  }

  const items = reviews.data?.items ?? []
  return (
    <>
      <StaffHeader title="Reviews">Choose which customer reviews appear on the public site.</StaffHeader>
      <StatusLine>{note}</StatusLine>
      <StatusLine error>{problem}</StatusLine>
      <div className="admin-filters">
        <label>
          Show
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
            <option value="pending">Waiting for a decision</option>
            <option value="approved">Shown on the site</option>
            <option value="">All reviews</option>
          </select>
        </label>
        <label>
          Artist
          <select value={artistId} onChange={(e) => { setArtistId(e.target.value); setPage(0) }}>
            <option value="">All</option>
            {artists.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </label>
      </div>
      <StaffState loading={reviews.loading && !reviews.data} error={reviews.error} onRetry={reviews.reload}
        empty={reviews.data && items.length === 0 && (status === "pending" ? "No reviews are waiting. New ones appear here first." : "No reviews match.")} />
      <div className="admin-review-list">
        {items.map((r) => (
          <article key={r.id} className="slip on-paper">
            <div className="review-score">
              {r.rating} <span>out of 5</span>
            </div>
            <p>{r.text}</p>
            <div className="review-meta">
              <span>
                {r.name} for {artistName(r.artistId)}
              </span>
              <time dateTime={r.createdAt}>{whenAt(r.createdAt).replace(/, \d\d:\d\d$/, "")}</time>
            </div>
            <div className="review-actions">
              <StaffSwitch label="Show on site" hiddenLabel={`Show ${r.name}'s review on the site`} checked={r.approved} onChange={(on) => show(r, on)} />
              <span className="table-actions">
                <button className="staff-text-button" type="button" onClick={() => setEditing(r)}>Edit</button>
                <button className="staff-text-button" type="button" onClick={() => setDeleting(r)}>Delete</button>
              </span>
            </div>
          </article>
        ))}
      </div>
      {reviews.data && <Pager page={reviews.data.page} totalPages={reviews.data.totalPages} onPage={setPage} />}

      {editing && (
        <ReviewEditDialog
          review={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            say("Review saved.")
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete this review?"
          confirmLabel="Delete review"
          onClose={() => setDeleting(null)}
          onConfirm={async () => {
            await staffApi(`/admin/reviews/${deleting.id}`, { method: "DELETE" })
            setDeleting(null)
            say("Review deleted.")
          }}
        >
          {deleting.name}'s review is removed for good. To keep it off the site but on file, switch off Show on site instead.
        </ConfirmDialog>
      )}
    </>
  )
}

/** Correct a review (a typo, or a surname to shorten) before showing it. */
function ReviewEditDialog({ review, onClose, onSaved }: { review: AdminReview; onClose: () => void; onSaved: () => void }) {
  const [values, setValues] = useState({ name: review.name, rating: String(review.rating), text: review.text, approved: review.approved })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!values.name.trim()) found.name = "Enter the name shown with the review."
    if (!values.text.trim()) found.text = "The review needs some text."
    setErrors(found)
    if (Object.keys(found).length) return
    setBusy(true)
    setProblem("")
    try {
      await staffApi(`/admin/reviews/${review.id}`, json("PUT", { name: values.name.trim(), rating: Number(values.rating), text: values.text.trim(), approved: values.approved }))
      onSaved()
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setErrors(reason.errors)
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  return (
    <Dialog title="Edit review" titleId="review-edit-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      <TextField label="Name" name="name" maxLength={limits["review.name"]} value={values.name} onChange={(e) => setValues({ ...values, name: e.target.value })} error={errors.name} />
      <SelectField label="Rating" name="rating" value={values.rating} onChange={(e) => setValues({ ...values, rating: e.target.value })}
        options={[5, 4, 3, 2, 1].map((n) => ({ value: String(n), label: `${n} out of 5` }))} />
      <TextAreaField label="Review" name="text" rows={5} maxLength={limits["review.text"]} value={values.text}
        onChange={(e) => setValues({ ...values, text: e.target.value })} error={errors.text} />
      <StaffSwitch label="Show on site" checked={values.approved} onChange={(approved) => setValues({ ...values, approved })} />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>{busy ? "Saving..." : "Save review"}</button>
        <button className="button button-secondary" type="button" onClick={onClose}>Cancel</button>
      </div>
    </Dialog>
  )
}
