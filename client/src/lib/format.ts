/** The studio's details, as on every page (the footer, the phone menu, the booking steps). */
export const studio = {
  name: "Blade & Brush",
  address: "Central Sofia, Bulgaria",
  hours: "Tuesday to Sunday, 11:00 to 23:00",
  closed: "Closed on Monday",
  phone: "+359 88 000 0000",
  tel: "tel:+359880000000",
}

/** 180 → "€180", 28.5 → "€28.50" */
export function euros(amount: number | null | undefined) {
  if (amount === null || amount === undefined) return ""
  return Number.isInteger(amount) ? `€${amount}` : `€${amount.toFixed(2)}`
}

/** 60 → "1 hour", 90 → "1.5 hours", 30 → "30 minutes" */
export function hours(minutes: number) {
  if (minutes < 60) return `${minutes} minutes`
  const h = minutes / 60
  return h === 1 ? "1 hour" : `${Number.isInteger(h) ? h : h.toFixed(1)} hours`
}

/** "6 × 8 cm" */
export const size = (widthCm: number | null, heightCm: number | null) =>
  widthCm && heightCm ? `${widthCm} × ${heightCm} cm` : ""

/** A "YYYY-MM-DD" date as a UTC date, so formatting never shifts it by the time zone. */
function day(date: string) {
  const [y, m, d] = date.split("-").map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}

const fmt = (date: string, options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", ...options }).format(day(date))

/** "Thursday, 5 November 2026" */
export const longDate = (date: string) =>
  `${fmt(date, { weekday: "long" })}, ${fmt(date, { day: "numeric", month: "long", year: "numeric" })}`

/** "Saturday 14 November 2026" (cards, no comma) */
export const cardDate = (date: string) =>
  `${fmt(date, { weekday: "long" })} ${fmt(date, { day: "numeric", month: "long", year: "numeric" })}`

/** "18 October 2026" */
export const plainDate = (date: string) => fmt(date, { day: "numeric", month: "long", year: "numeric" })

/** "14 Nov 2026" */
export const shortDate = (date: string) => fmt(date, { day: "numeric", month: "short", year: "numeric" })

/** { weekday: "Thu", date: "5 Nov" } for the day strip */
export const dayButton = (date: string) => ({
  weekday: fmt(date, { weekday: "short" }),
  date: fmt(date, { day: "numeric", month: "short" }),
})

/** The studio's date today, "YYYY-MM-DD". */
export const todayInSofia = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Sofia" }).format(new Date())

/** "dd.mm.yyyy" (what people type) → "YYYY-MM-DD", or null if it isn't a real date. */
export function parseTypedDate(value: string): string | null {
  const m = /^\s*(\d{1,2})[./-](\d{1,2})[./-](\d{4})\s*$/.exec(value)
  if (!m) return null
  const [d, mo, y] = [Number(m[1]), Number(m[2]), Number(m[3])]
  const check = new Date(Date.UTC(y, mo - 1, d))
  if (check.getUTCFullYear() !== y || check.getUTCMonth() !== mo - 1 || check.getUTCDate() !== d) return null
  return `${y}-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}`
}

/** Whole years between a birth date and a day ("YYYY-MM-DD"). */
export function ageOn(birth: string, on: string) {
  const [by, bm, bd] = birth.split("-").map(Number)
  const [y, m, d] = on.split("-").map(Number)
  return y - by - (m < bm || (m === bm && d < bd) ? 1 : 0)
}

/** "mm:ss" left until `iso`, or null once it has passed. */
export function countdown(iso: string, now = Date.now()) {
  const left = Math.floor((new Date(iso).getTime() - now) / 1000)
  if (left <= 0) return null
  return `${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`
}

export const placements: { value: string; label: string }[] = [
  { value: "FOREARM", label: "Forearm" },
  { value: "UPPER_ARM", label: "Upper arm" },
  { value: "FULL_SLEEVE", label: "Full sleeve" },
  { value: "SHOULDER", label: "Shoulder" },
  { value: "CHEST", label: "Chest" },
  { value: "BACK", label: "Back" },
  { value: "RIBS", label: "Ribs" },
  { value: "THIGH", label: "Thigh" },
  { value: "CALF", label: "Calf" },
  { value: "OTHER", label: "Other" },
]

export const colours: { value: string; label: string }[] = [
  { value: "FULL_COLOUR", label: "Full colour" },
  { value: "BLACK_AND_GREY", label: "Black and grey" },
  { value: "NOT_SURE", label: "Not sure" },
]

export const placementLabel = (value: string | null) => placements.find((p) => p.value === value)?.label ?? ""
export const colourLabel = (value: string | null) => colours.find((c) => c.value === value)?.label ?? ""
