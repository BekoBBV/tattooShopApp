import { useState, type FormEvent } from "react"
import { cancelQuestion } from "../lib/bookingText"
import { limits } from "../lib/limits"
import type { Booking } from "../lib/types"
import { checkReview, useFieldChecks } from "../lib/validation"
import { Dialog, FormError, TextAreaField } from "./Studio"

/** "Cancel this booking?" with the deposit rule spelled out. */
export function CancelDialog({ booking, onCancel, onClose }: { booking: Booking; onCancel: () => Promise<void>; onClose: () => void }) {
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  async function confirm() {
    setBusy(true)
    setProblem("")
    try {
      await onCancel()
    } catch (reason) {
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
      setBusy(false)
    }
  }
  return (
    <Dialog title="Cancel this booking?" titleId="cancel-title" onClose={onClose}>
      <p>{cancelQuestion(booking).replace("Cancel this booking? ", "")}</p>
      <FormError>{problem}</FormError>
      <div className="dialog-actions">
        <button className="button button-primary" type="button" onClick={confirm} disabled={busy}>
          {busy ? "Cancelling..." : "Cancel booking"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose} data-autofocus>
          Keep booking
        </button>
      </div>
    </Dialog>
  )
}

/** Stars (1–5) and a few words about a finished tattoo. */
export function ReviewDialog({
  booking,
  onSend,
  onClose,
}: {
  booking: Booking
  onSend: (review: { rating: number; text: string }) => Promise<void>
  onClose: () => void
}) {
  const [rating, setRating] = useState(0)
  const [text, setText] = useState("")
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  const errors = checkReview({ rating, text })
  const checks = useFieldChecks(errors)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    try {
      await onSend({ rating, text: text.trim() })
    } catch (reason) {
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
      setBusy(false)
    }
  }

  return (
    <Dialog title={`Review ${booking.title ?? "your tattoo"}`} titleId="review-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <fieldset className="review-rating">
        <legend>Rating</legend>
        <div>
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className={rating === n ? "is-selected" : ""}>
              <input type="radio" name="rating" value={n} checked={rating === n} onChange={() => setRating(n)} />
              {n}
            </label>
          ))}
        </div>
        {checks.errorFor("rating") && <p className="field-error">{checks.errorFor("rating")}</p>}
      </fieldset>
      <TextAreaField label="Your review" name="text" rows={4} maxLength={limits["review.text"]} value={text}
        onChange={(e) => setText(e.target.value)}
        error={checks.errorFor("text")} />
      <p className="helper">Your first name is shown with it, after the studio checks it.</p>
      <FormError>{problem}</FormError>
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Sending..." : "Send review"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>
          Cancel
        </button>
      </div>
    </Dialog>
  )
}
