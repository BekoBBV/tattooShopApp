import { useState, type FormEvent, type ReactNode } from "react"
import { euros, longDate } from "../lib/format"
import { json, staffApi } from "../lib/staff"
import { aboutRows, bookingTitle, canCancel, canMarkOutcome, depositStamp, statusLabel, typeLabel } from "../lib/staffText"
import type { BookingStatus, StaffBooking } from "../lib/types"
import { limits } from "../lib/limits"
import { errorText } from "../lib/useLoad"
import { Lightbox } from "./Lightbox"
import { ConfirmDialog, NightStamp, SidePanel } from "./StaffUi"
import { Dialog, FormError, TextAreaField, TextField } from "./Studio"

type Props = {
  booking: StaffBooking
  /** The admin sees every artist's bookings and can do a little more. */
  admin: boolean
  onClose: () => void
  /** After a change: the booking as it is now (null when deleted) and a note for the page. */
  onChanged: (booking: StaffBooking | null, note: string) => void
  /** Artists reserve a follow-up session from a consultation. */
  onReserve?: (booking: StaffBooking) => void
}

/** Everything about one booking, with what staff can do with it. */
export function StaffBookingPanel({ booking: b, admin, onClose, onChanged, onReserve }: Props) {
  const [ask, setAsk] = useState<null | "cancel" | "delete" | "edit">(null)
  const [viewing, setViewing] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  const base = admin ? `/admin/bookings/${b.id}` : `/staff/bookings/${b.id}`
  const deposit = depositStamp(b)
  const photos = b.consultation?.photos ?? []

  async function setStatus(status: BookingStatus, note: string) {
    setBusy(true)
    setProblem("")
    try {
      onChanged(await staffApi<StaffBooking>(base, json("PATCH", { status })), note)
    } catch (reason) {
      setProblem(errorText(reason))
    } finally {
      setBusy(false)
    }
  }

  async function paidInCash() {
    setBusy(true)
    setProblem("")
    try {
      onChanged(await staffApi<StaffBooking>(`${base}/deposit-cash`, { method: "POST" }), "Deposit marked as paid in cash.")
    } catch (reason) {
      setProblem(errorText(reason))
    } finally {
      setBusy(false)
    }
  }

  const rows: [string, ReactNode][] = [
    ["Time", `${longDate(b.date)}, ${b.time} to ${b.endTime}`],
    ["Status", statusLabel[b.status]],
    ["Type", typeLabel[b.type]],
  ]
  if (admin && b.artistName) rows.push(["Artist", b.artistName])
  if (b.anonymized) rows.push(["Customer", "Details removed for privacy"])
  else {
    rows.push(["Customer", b.customerName ?? ""])
    if (b.phone) rows.push(["Phone", <a key="phone" href={`tel:${b.phone.replace(/[^+\d]/g, "")}`}>{b.phone}</a>])
    if (b.email) rows.push(["Email", <a key="email" href={`mailto:${b.email}`}>{b.email}</a>])
  }
  for (const row of aboutRows(b)) rows.push(row)
  if (b.price !== null) rows.push(["Price", euros(b.price)])
  rows.push([
    "Deposit",
    <>
      <NightStamp tone={deposit.tone === "paid" ? "plain" : "muted"}>{deposit.label}</NightStamp>
      {deposit.note && <small className="panel-warning">{deposit.note}</small>}
    </>,
  ])
  if (b.notes) rows.push(["Notes", b.notes])
  rows.push(["Booking code", b.code])

  return (
    <SidePanel title={bookingTitle(b)} label="Booking details" onClose={onClose}>
      {b.awaitingStatus && <p className="panel-callout">This appointment is over. Mark it Done or No-show.</p>}
      <dl className="slip-list">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      {photos.length > 0 && (
        <>
          <h3>Reference photos</h3>
          <div className="reference-row">
            {photos.map((photo, i) => (
              <button key={photo.id} type="button" aria-label={`Open reference photo ${i + 1}`} onClick={() => setViewing(i)}>
                <img src={photo.thumbUrl} alt="" />
              </button>
            ))}
          </div>
        </>
      )}
      <FormError>{problem}</FormError>
      <div className="panel-actions">
        {canMarkOutcome(b) && (
          <div className="panel-action-row">
            <button className="button button-primary" type="button" disabled={busy || b.status === "DONE"}
              onClick={() => setStatus("DONE", "Marked as done.")}>
              Done
            </button>
            <button className="button button-secondary" type="button" disabled={busy || b.status === "NO_SHOW"}
              onClick={() => setStatus("NO_SHOW", "Marked as a no-show.")}>
              No-show
            </button>
          </div>
        )}
        {onReserve && b.type === "CONSULTATION" && !b.anonymized && (b.status === "BOOKED" || b.status === "DONE") && (
          <button className="button button-primary" type="button" onClick={() => onReserve(b)}>
            Reserve a session
          </button>
        )}
        {admin && b.deposit.status === "WAITING" && canCancel(b) && (
          <button className="button button-secondary" type="button" disabled={busy} onClick={paidInCash}>
            Mark deposit paid in cash
          </button>
        )}
        {admin && !b.anonymized && (
          <button className="staff-text-button" type="button" onClick={() => setAsk("edit")}>
            Edit customer details
          </button>
        )}
        {canCancel(b) && (
          <button className="staff-text-button" type="button" onClick={() => setAsk("cancel")}>
            Cancel booking
          </button>
        )}
        {admin && (
          <button className="staff-text-button" type="button" onClick={() => setAsk("delete")}>
            Delete for good
          </button>
        )}
      </div>

      {ask === "cancel" && (
        <ConfirmDialog
          title="Cancel this booking?"
          confirmLabel="Cancel booking"
          keepLabel="Keep booking"
          onClose={() => setAsk(null)}
          onConfirm={async () => {
            const updated = await staffApi<StaffBooking>(base, json("PATCH", { status: "CANCELLED" }))
            setAsk(null)
            onChanged(updated, "Booking cancelled. The customer gets an email, and a paid deposit is refunded.")
          }}
        >
          {b.customerName ?? "The customer"} gets an email that the studio cancelled.
          {b.deposit.status === "PAID" ? ` The ${euros(b.deposit.amount)} deposit is refunded.` : ""}
        </ConfirmDialog>
      )}
      {ask === "delete" && (
        <ConfirmDialog
          title="Delete this booking for good?"
          confirmLabel="Delete booking"
          onClose={() => setAsk(null)}
          onConfirm={async () => {
            await staffApi(base, { method: "DELETE" })
            setAsk(null)
            onChanged(null, "Booking deleted.")
          }}
        >
          It disappears with its photos and no email is sent. To tell the customer, cancel it instead.
        </ConfirmDialog>
      )}
      {ask === "edit" && (
        <CustomerDialog
          booking={b}
          onClose={() => setAsk(null)}
          onSaved={(updated) => {
            setAsk(null)
            onChanged(updated, "Customer details saved.")
          }}
        />
      )}
      {viewing !== null && (
        <Lightbox
          photos={photos.map((image, i) => ({ image, caption: `Reference photo ${i + 1}` }))}
          index={viewing}
          label="Reference photos"
          onChange={setViewing}
          onClose={() => setViewing(null)}
        />
      )}
    </SidePanel>
  )
}

/** The admin corrects a customer's name, phone, email or the studio's notes. */
function CustomerDialog({ booking, onClose, onSaved }: { booking: StaffBooking; onClose: () => void; onSaved: (b: StaffBooking) => void }) {
  const [values, setValues] = useState({
    customerName: booking.customerName ?? "",
    phone: booking.phone ?? "",
    email: booking.email ?? "",
    notes: booking.notes ?? "",
  })
  const [problem, setProblem] = useState("")
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!values.customerName.trim()) found.customerName = "Enter the customer's name."
    if (!values.phone.trim()) found.phone = "Enter a phone number."
    setErrors(found)
    if (Object.keys(found).length) return
    setBusy(true)
    setProblem("")
    try {
      onSaved(
        await staffApi<StaffBooking>(
          `/admin/bookings/${booking.id}`,
          json("PUT", {
            customerName: values.customerName.trim(),
            phone: values.phone.trim(),
            email: values.email.trim() || null,
            notes: values.notes.trim() || null,
          }),
        ),
      )
    } catch (reason) {
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  return (
    <Dialog title="Customer details" titleId="customer-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      <TextField label="Name" name="customerName" maxLength={limits["booking.customerName"]} value={values.customerName}
        onChange={set("customerName")} error={errors.customerName} />
      <TextField label="Phone" name="phone" type="tel" maxLength={limits["booking.phone"]} value={values.phone} onChange={set("phone")} error={errors.phone} />
      <TextField label="Email" name="email" type="email" maxLength={limits["booking.email"]} value={values.email} onChange={set("email")} />
      <TextAreaField label="Studio notes" name="notes" rows={3} maxLength={limits["booking.notes"]} value={values.notes} onChange={set("notes")}
        helper="Only staff see these." />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Saving..." : "Save details"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>
          Cancel
        </button>
      </div>
    </Dialog>
  )
}
