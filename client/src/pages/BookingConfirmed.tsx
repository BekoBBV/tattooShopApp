import { CalendarPlus } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { Link, useSearchParams } from "react-router"
import { Stamp } from "../components/Studio"
import { api } from "../lib/api"
import { useBookingDraft } from "../lib/bookingDraft"
import { calendarFile, statusOf, titleOf, whenOf } from "../lib/bookingText"
import { useCustomer } from "../lib/customer"
import { euros } from "../lib/format"
import type { Booking, Page } from "../lib/types"
import { usePageTitle } from "../lib/usePageTitle"

/** "You're booked": the seal stamps down, the code, the details and what to do before coming. */
export default function BookingConfirmed() {
  const [params] = useSearchParams()
  const code = params.get("code") ?? ""
  const draft = useBookingDraft()
  const customer = useCustomer()
  const [booking, setBooking] = useState<Booking | null>(null)
  usePageTitle(booking?.type === "CONSULTATION" ? "Your consultation is booked" : "You're booked")

  // The booking is looked up with the phone number from this tab's booking, or from the account
  useEffect(() => {
    let current = true
    const phone = draft.code === code ? draft.phone : null
    const request = phone
      ? api<Booking>("/bookings/lookup", { method: "POST", body: JSON.stringify({ code, phone }) })
      : customer
        ? api<Page<Booking>>(`/account/bookings?when=ALL&q=${encodeURIComponent(code)}`, { token: customer.token }).then(
            (page) => page.items.find((b) => b.code === code) ?? null,
          )
        : Promise.resolve(null)
    request.then((b) => current && setBooking(b)).catch(() => current && setBooking(null))
    return () => {
      current = false
    }
  }, [code, draft.code, draft.phone, customer])

  const calendar = useMemo(() => (booking ? calendarFile(booking) : null), [booking])
  useEffect(() => () => {
    if (calendar) URL.revokeObjectURL(calendar)
  }, [calendar])

  const consultation = booking?.type === "CONSULTATION"
  return (
    <main>
      <div className="page-banner" aria-hidden="true">
        <img src="/images/footer-night.jpg" alt="" />
      </div>
      <section className="confirmation-hero" aria-labelledby="confirmed-title">
        <div className="confirmation-seal">
          <img src="/images/seal-bb.png" alt="" />
        </div>
        <h1 id="confirmed-title">{consultation ? "Your consultation is booked" : "You're booked"}</h1>
        <p className="booking-code">{code}</p>
        <p>Keep this code and your phone number. You need both to manage or cancel your booking.</p>
      </section>
      {booking ? (
        <section className="wrap confirmation-details">
          <div className="slip on-paper">
            <h2>Booking details</h2>
            <dl className="slip-list">
              <div>
                <dt>What</dt>
                <dd>{consultation ? "Free consultation, 30 minutes" : titleOf(booking)}</dd>
              </div>
              <div>
                <dt>Artist</dt>
                <dd>{booking.artistName}</dd>
              </div>
              <div>
                <dt>Date and time</dt>
                <dd>{whenOf(booking)}</dd>
              </div>
              {booking.deposit.status === "PAID" && (
                <div>
                  <dt>Deposit paid</dt>
                  <dd>{euros(booking.deposit.amount)}</dd>
                </div>
              )}
              {booking.leftToPay !== null && (
                <div className="total">
                  <dt>Left to pay in the studio</dt>
                  <dd>{euros(booking.leftToPay)}</dd>
                </div>
              )}
            </dl>
            {consultation && booking.consultation && booking.consultation.photos.length > 0 && (
              <div className="consultation-thumbnails">
                {booking.consultation.photos.map((photo, i) => (
                  <figure key={photo.id}>
                    <img src={photo.thumbUrl} alt="" />
                    <figcaption>Reference {i + 1}</figcaption>
                  </figure>
                ))}
              </div>
            )}
            <div className="slip-stamp">
              <Stamp tone={statusOf(booking).tone}>{statusOf(booking).label}</Stamp>
            </div>
          </div>
          <div className="before-visit">
            <h2>Before you come</h2>
            <ul>
              {consultation ? (
                <>
                  <li>Bring your ideas and any other references.</li>
                  <li>Think about where on the body you would like it.</li>
                  <li>Your artist will plan the piece and the sessions with you.</li>
                </>
              ) : (
                <>
                  <li>Eat a proper meal before your appointment.</li>
                  <li>Bring a valid photo ID.</li>
                  <li>Wear clothes that leave the placement free.</li>
                  <li>Do not drink alcohol the day before.</li>
                </>
              )}
            </ul>
          </div>
        </section>
      ) : (
        <section className="wrap confirmation-details">
          <p className="state-note">
            You can see the details at any time on <Link to="/manage">Manage a booking</Link> with this code and your phone number.
          </p>
        </section>
      )}
      <nav className="wrap confirmation-links" aria-label="Booking next steps">
        {calendar && booking && (
          <a className="button button-secondary" href={calendar} download={`blade-and-brush-${booking.code}.ics`}>
            <CalendarPlus className="icon" size={18} aria-hidden="true" /> Add to calendar
          </a>
        )}
        <Link className="text-link" to="/aftercare">
          Read the aftercare guide
        </Link>
        <Link className="text-link" to="/">
          Back to home
        </Link>
      </nav>
    </main>
  )
}
