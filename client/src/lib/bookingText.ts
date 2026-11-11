import { euros, hours, longDate, size } from "./format"
import type { Booking, Flash } from "./types"

/** The words the site uses for a booking's state, and how its stamp looks. */
export function statusOf(b: Booking): { label: string; tone: "red" | "waiting" | "done" } {
  switch (b.status) {
    case "PENDING":
      return { label: "Waiting for deposit", tone: "waiting" }
    case "BOOKED":
      if (b.deposit.status === "WAITING") return { label: "Waiting for deposit", tone: "waiting" }
      if (b.type === "CONSULTATION") return { label: "Consultation booked", tone: "red" }
      return { label: b.deposit.status === "PAID" ? "Deposit paid" : "Booked", tone: "red" }
    case "CANCELLED":
      return { label: b.deposit.status === "REFUNDED" ? "Deposit refunded" : "Cancelled", tone: "done" }
    case "EXPIRED":
      return { label: "Hold expired", tone: "done" }
    case "DONE":
      return { label: "Finished", tone: "done" }
    case "NO_SHOW":
      return { label: "Missed", tone: "done" }
  }
}

/** What the booking is for: "Small dragon", "Back piece, session 1 of 3" or "Consultation with Nika". */
export function titleOf(b: Booking) {
  if (b.title) return b.title
  return `Consultation with ${b.artistName ?? "your artist"}`
}

/** "Session reserved by Dario", "Consultation" or "Flash" (the line above the title in My account). */
export function kindOf(b: Booking) {
  if (b.type === "SESSION") return `Session reserved by ${b.artistName ?? "your artist"}`
  if (b.type === "CONSULTATION") return "Consultation"
  return "Flash"
}

/** "Thursday, 5 November 2026, 14:00" */
export const whenOf = (b: Booking) => `${longDate(b.date)}, ${b.time}`

/** The cancel dialog's text, depending on the 48-hour refund rule. */
export function cancelQuestion(b: Booking) {
  const amount = euros(b.deposit.amount)
  if (b.deposit.status === "PAID") {
    return b.refundIfCancelledNow
      ? `Cancel this booking? Your ${amount} deposit will be refunded to your card.`
      : `Cancel this booking? It's less than 48 hours away, so the ${amount} deposit can't be refunded.`
  }
  return "Cancel this booking? The time is given to someone else."
}

/** What happened after cancelling. */
export function cancelledNote(b: Booking) {
  if (b.deposit.status === "REFUNDED") return `Booking cancelled. Your ${euros(b.deposit.amount)} deposit will be refunded to your card.`
  if (b.deposit.status === "KEPT") return `Booking cancelled. The ${euros(b.deposit.amount)} deposit is kept, as it was less than 48 hours ahead.`
  return "Booking cancelled."
}

/** An .ics calendar file for "Add to calendar". */
export function calendarFile(b: Booking) {
  const stamp = (iso: string) => iso.replace(/[-:]/g, "").replace(/\.\d{3}/, "")
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Blade & Brush//Booking//EN",
    "BEGIN:VEVENT",
    `UID:${b.code}@bladeandbrush`,
    `DTSTAMP:${stamp(new Date().toISOString())}`,
    `DTSTART:${stamp(b.startsAt)}`,
    `DTEND:${stamp(b.endsAt)}`,
    `SUMMARY:${titleOf(b).replace(/[,;]/g, " ")} at Blade & Brush`,
    `DESCRIPTION:Booking code ${b.code}. Bring an ID.`,
    "LOCATION:Blade & Brush\\, Central Sofia",
    "END:VEVENT",
    "END:VCALENDAR",
  ]
  return URL.createObjectURL(new Blob([lines.join("\r\n")], { type: "text/calendar" }))
}

export type SummaryRow = { label: string; value: string; total?: boolean }

/**
 * The rows of the "Your booking" summary beside the Time and Details steps: what was chosen so far
 * and, for a flash design, what is paid now and what is left for the studio.
 */
export function summaryRows(chosen: {
  kind: "flash" | "consultation"
  flash: Pick<Flash, "widthCm" | "heightCm" | "durationMinutes" | "price" | "deposit"> | null
  artistName: string | null
  date: string | null
  time: string | null
}): SummaryRow[] {
  const { kind, flash } = chosen
  const rows: SummaryRow[] = []
  if (chosen.artistName) rows.push({ label: "Artist", value: chosen.artistName })
  if (kind === "flash" && flash) rows.push({ label: "Size", value: size(flash.widthCm, flash.heightCm) })
  if (kind === "consultation") rows.push({ label: "Length", value: "30 minutes" })
  else if (flash) rows.push({ label: "Length", value: `About ${hours(flash.durationMinutes)}` })
  rows.push({ label: "When", value: chosen.date && chosen.time ? `${longDate(chosen.date)}, ${chosen.time}` : "Not chosen yet" })
  if (kind === "consultation") rows.push({ label: "Price", value: "Free" })
  else if (flash) {
    rows.push({ label: "Price", value: euros(flash.price) })
    rows.push({ label: "Deposit now", value: euros(flash.deposit) })
    rows.push({ label: "Pay in the studio", value: euros(flash.price - flash.deposit), total: true })
  }
  return rows
}
