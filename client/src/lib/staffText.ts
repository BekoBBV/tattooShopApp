import { placementLabel, colourLabel, euros, shortDate } from "./format"
import type { BookingStatus, BookingType, DepositStatus, FlashState, StaffBooking, Weekday, WorkingHours } from "./types"

/** How the staff pages name each booking type. */
export const typeLabel: Record<BookingType, string> = {
  FLASH: "Flash",
  CONSULTATION: "Consultation",
  SESSION: "Reserved session",
}

export const statusLabel: Record<BookingStatus, string> = {
  PENDING: "Held for deposit",
  BOOKED: "Booked",
  CANCELLED: "Cancelled",
  EXPIRED: "Hold expired",
  DONE: "Done",
  NO_SHOW: "No-show",
}

/** The deposit as a night stamp: its words and look, plus a warning line when it is late. */
export function depositStamp(b: StaffBooking): { label: string; tone: "paid" | "waiting" | "muted"; note: string | null } {
  const d = b.deposit
  const late = b.depositOverdue ? (b.type === "FLASH" ? "Hold expired" : "Deposit overdue") : null
  switch (d.status as DepositStatus) {
    case "NONE":
      return { label: "No deposit", tone: "muted", note: null }
    case "WAITING":
      return { label: `Waiting, ${euros(d.amount)}`, tone: "waiting", note: late }
    case "PAID":
      return { label: d.method === "CASH" ? "Paid in cash" : "Paid with Revolut", tone: "paid", note: null }
    case "REFUNDED":
      return { label: "Refunded", tone: "muted", note: null }
    case "KEPT":
      return { label: "Kept", tone: "muted", note: null }
    default:
      return { label: "Not paid", tone: "muted", note: null }
  }
}

/** The title of a booking on the calendar and in the panel. */
export const bookingTitle = (b: StaffBooking) =>
  b.title ?? (b.type === "CONSULTATION" ? b.consultation?.idea?.split(/[.,\n]/)[0]?.slice(0, 60) || "Consultation" : typeLabel[b.type])

/** The details panel's rows: what the booking is about. */
export function aboutRows(b: StaffBooking): [string, string][] {
  const rows: [string, string][] = []
  if (b.type === "CONSULTATION" && b.consultation) {
    const c = b.consultation
    if (c.idea) rows.push(["Idea", c.idea])
    if (c.placement) rows.push(["Placement", placementLabel(c.placement)])
    if (c.sizeCm) rows.push(["Size", `Approximately ${c.sizeCm} cm`])
    if (c.colour) rows.push(["Colour", colourLabel(c.colour)])
  } else if (b.title) {
    rows.push([b.type === "FLASH" ? "Design" : "Session", b.title])
    if (b.widthCm && b.heightCm) rows.push(["Size", `${b.widthCm} × ${b.heightCm} cm`])
  }
  return rows
}

/** "Held for Lucky koi (booking 68CB-A5)" and the like, for the flash lists. */
export const flashStateLabel: Record<FlashState, string> = {
  ON_SHEET: "On the sheet",
  HIDDEN: "Hidden",
  HELD: "Held for a deposit",
  BOOKED: "Booked",
  TATTOOED: "Tattooed",
}

/** A booking's date for the tables: "14 Nov 2026". */
export const tableDate = (b: StaffBooking) => shortDate(b.date)

/** True once the appointment has started, so it can be marked Done or No-show. */
export const hasStarted = (b: StaffBooking) => new Date(b.startsAt).getTime() <= Date.now()

/** A held or booked appointment can still be cancelled. */
export const canCancel = (b: StaffBooking) => b.status === "PENDING" || b.status === "BOOKED"

/** Done / No-show once it has started (and switching between the two corrects a mistake). */
export const canMarkOutcome = (b: StaffBooking) =>
  hasStarted(b) && (b.status === "BOOKED" || b.status === "DONE" || b.status === "NO_SHOW")

export const DAY_NAMES: Record<Weekday, string> = {
  MONDAY: "Monday",
  TUESDAY: "Tuesday",
  WEDNESDAY: "Wednesday",
  THURSDAY: "Thursday",
  FRIDAY: "Friday",
  SATURDAY: "Saturday",
  SUNDAY: "Sunday",
}
export const WEEK: Weekday[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

/** "Tuesday to Sunday, 11:00 to 23:00" when the open days share their hours, otherwise day by day. */
export function hoursSummary(weekly: WorkingHours[]) {
  const open = WEEK.map((day) => weekly.find((h) => h.day === day)).filter((h): h is WorkingHours => !!h)
  if (open.length === 0) return "No weekly hours set yet"
  const same = open.every((h) => h.start === open[0].start && h.end === open[0].end)
  const firstIndex = WEEK.indexOf(open[0].day)
  const runs = open.every((h, i) => WEEK.indexOf(h.day) === firstIndex + i)
  if (same && runs && open.length > 1)
    return `${DAY_NAMES[open[0].day]} to ${DAY_NAMES[open[open.length - 1].day]}, ${open[0].start} to ${open[0].end}`
  return open.map((h) => `${DAY_NAMES[h.day].slice(0, 3)} ${h.start} to ${h.end}`).join(", ")
}


/** An instant in studio time: "Today, 09:42", "Yesterday, 16:15" or "8 November 2026, 16:15". */
export function whenAt(iso: string | null, now = new Date()) {
  if (!iso) return ""
  const zone = "Europe/Sofia"
  const day = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: zone }).format(d)
  const at = new Date(iso)
  const time = new Intl.DateTimeFormat("en-GB", { timeZone: zone, hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(at)
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  if (day(at) === day(now)) return `Today, ${time}`
  if (day(at) === day(yesterday)) return `Yesterday, ${time}`
  return `${new Intl.DateTimeFormat("en-GB", { timeZone: zone, day: "numeric", month: "long", year: "numeric" }).format(at)}, ${time}`
}

/** "1st", "2nd", "3rd", "4th" … */
export const ordinal = (n: number) => `${n}${n % 100 >= 11 && n % 100 <= 13 ? "th" : ({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`
