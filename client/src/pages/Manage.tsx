import { useState, type FormEvent } from "react"
import { Link, useSearchParams } from "react-router"
import { CancelDialog, ReviewDialog } from "../components/BookingDialogs"
import { PageIntro } from "../components/Layout"
import { FormError, Stamp, TextField } from "../components/Studio"
import { api } from "../lib/api"
import { getDraft } from "../lib/bookingDraft"
import { cancelledNote, statusOf, titleOf } from "../lib/bookingText"
import { euros, longDate, placementLabel } from "../lib/format"
import { limits } from "../lib/limits"
import type { Booking, PaymentStart } from "../lib/types"
import { usePageTitle } from "../lib/usePageTitle"
import { checkLookup, useFieldChecks } from "../lib/validation"

/** Find a booking with its code and phone number: see it, pay a waiting deposit, cancel it or review it. */
export default function Manage() {
  usePageTitle("Manage a booking")
  const [params] = useSearchParams()
  const draft = getDraft()
  const linkCode = params.get("code") ?? ""
  const [code, setCode] = useState(linkCode)
  const [phone, setPhone] = useState(linkCode && draft.code === linkCode ? (draft.phone ?? "") : "")
  const [booking, setBooking] = useState<Booking | null>(null)
  const [found, setFound] = useState<{ code: string; phone: string } | null>(null)
  const [problem, setProblem] = useState("")
  const [note, setNote] = useState(params.get("refunded") ? "The payment came in after the 15-minute hold had run out and the time was taken, so your deposit is refunded." : "")
  const [busy, setBusy] = useState(false)
  const [dialog, setDialog] = useState<"cancel" | "review" | null>(null)
  const errors = checkLookup(code, phone)
  const checks = useFieldChecks(errors)

  async function find(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    try {
      const b = await api<Booking>("/bookings/lookup", { method: "POST", body: JSON.stringify({ code, phone }) })
      setBooking(b)
      setFound({ code: b.code, phone })
    } catch (reason) {
      setBooking(null)
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
    } finally {
      setBusy(false)
    }
  }

  async function pay() {
    if (!found) return
    setBusy(true)
    setProblem("")
    try {
      const start = await api<PaymentStart>("/bookings/deposit", { method: "POST", body: JSON.stringify(found) })
      window.location.assign(start.checkoutUrl)
    } catch (reason) {
      setBusy(false)
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
    }
  }

  const status = booking ? statusOf(booking) : null
  return (
    <main>
      <PageIntro title="Manage a booking">
        <p>Find your booking with the code from your confirmation.</p>
      </PageIntro>
      <section className="wrap booking-body manage-layout">
        <div className="slip on-paper">
          <h2>Find a booking</h2>
          <form onSubmit={find} onChange={checks.onChange} onBlur={checks.onBlur} noValidate>
            <TextField label="Booking code" name="code" placeholder="K7Q2-M9" autoCapitalize="characters" maxLength={limits["lookup.code"]}
              value={code} onChange={(e) => setCode(e.target.value)} error={checks.errorFor("code")} />
            <TextField label="Phone number" name="phone" type="tel" autoComplete="tel" placeholder="+359" maxLength={limits["lookup.phone"]}
              value={phone} onChange={(e) => setPhone(e.target.value)} error={checks.errorFor("phone")} />
            <button className="button button-primary" type="submit" disabled={busy && !booking}>
              {busy && !booking ? "Looking..." : "Find booking"}
            </button>
          </form>
        </div>
        <div className="slip on-paper" aria-live="polite">
          <FormError>{problem}</FormError>
          {note && (
            <p className="form-sent" role="status">
              {note}
            </p>
          )}
          {booking && status ? (
            <>
              <div className="managed-heading">
                <div>
                  <h2>{titleOf(booking)}</h2>
                  <p className="record-code">{booking.code}</p>
                </div>
                <Stamp tone={status.tone}>{status.label}</Stamp>
              </div>
              <dl className="slip-list">
                <div>
                  <dt>Artist</dt>
                  <dd>{booking.artistName}</dd>
                </div>
                <div>
                  <dt>Date</dt>
                  <dd>{longDate(booking.date)}</dd>
                </div>
                <div>
                  <dt>Time</dt>
                  <dd>
                    {booking.time} to {booking.endTime}
                  </dd>
                </div>
                {booking.consultation?.placement && (
                  <div>
                    <dt>Placement</dt>
                    <dd>{placementLabel(booking.consultation.placement)}</dd>
                  </div>
                )}
                {booking.price !== null && (
                  <div>
                    <dt>Price</dt>
                    <dd>{euros(booking.price)}</dd>
                  </div>
                )}
                {booking.deposit.amount !== null && (
                  <div>
                    <dt>Deposit</dt>
                    <dd>{euros(booking.deposit.amount)}</dd>
                  </div>
                )}
              </dl>
              {(booking.canPayDeposit || booking.cancellable || booking.canReview) && (
                <div className="managed-actions">
                  {booking.canPayDeposit && (
                    <button className="button button-primary" type="button" onClick={pay} disabled={busy}>
                      {busy ? "Taking you to Revolut to pay..." : `Pay ${euros(booking.deposit.amount)} deposit with Revolut`}
                    </button>
                  )}
                  {booking.canReview && (
                    <button className="button button-primary" type="button" onClick={() => setDialog("review")}>
                      Leave a review
                    </button>
                  )}
                  {booking.cancellable && (
                    <button className="button button-secondary" type="button" onClick={() => setDialog("cancel")}>
                      Cancel booking
                    </button>
                  )}
                </div>
              )}
              <button
                className="text-button"
                type="button"
                onClick={() => {
                  setBooking(null)
                  setFound(null)
                  setNote("")
                  setCode("")
                }}
              >
                Find another booking
              </button>
            </>
          ) : (
            !problem &&
            !note && (
              <p className="state-note">
                Enter the booking code from your confirmation and the phone number you booked with. Lost the code? Call the
                studio. <Link to="/account">Signed up?</Link> Your bookings are in My account too.
              </p>
            )
          )}
        </div>
      </section>
      {dialog === "cancel" && booking && found && (
        <CancelDialog
          booking={booking}
          onClose={() => setDialog(null)}
          onCancel={async () => {
            const b = await api<Booking>("/bookings/cancel", { method: "POST", body: JSON.stringify(found) })
            setBooking(b)
            setNote(cancelledNote(b))
            setDialog(null)
          }}
        />
      )}
      {dialog === "review" && booking && found && (
        <ReviewDialog
          booking={booking}
          onClose={() => setDialog(null)}
          onSend={async (review) => {
            await api("/bookings/review", { method: "POST", body: JSON.stringify({ ...found, ...review }) })
            setBooking({ ...booking, canReview: false })
            setNote("Thank you for the review. It appears on the artist's page once the studio has checked it.")
            setDialog(null)
          }}
        />
      )}
    </main>
  )
}
