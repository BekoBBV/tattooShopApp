/**
 * A pretend server for trying the website without the Spring Boot API and its database
 * (`npm run dev:mock`). It answers /api calls in the browser from sample data, following the same
 * rules and shapes as the real API (docs/api.md): times on a 30-minute grid with a 30-minute break
 * after each client, every flash design booked once, a 15-minute deposit hold, refunds 48 hours
 * ahead. Deposits are "paid" at once. The data is kept in this browser tab (sessionStorage), so a
 * reload keeps it and a new tab starts fresh. Customers sign in as mira@example.com, staff as admin,
 * kai, dario or nika, all with any password of 8+ characters.
 */
import type {
  AdminArtist,
  AdminFlash,
  AdminUser,
  Artist,
  Availability,
  Booking,
  BookingStatus,
  BookingType,
  ColourChoice,
  DepositStatus,
  Flash,
  FlashSize,
  DayException,
  Image,
  Message,
  Placement,
  Review,
  StaffBooking,
  Work,
  WorkingHours,
} from "../lib/types"

type Stored = Booking & { phone: string; email: string; userId: string | null; slots: number[]; reviewed: boolean; notes?: string | null; createdAt?: string }

const MIN = 60_000
const DEMO_EMAIL = "mira@example.com"

// ---- Time in the studio's zone ----

function offsetFor(date: string) {
  const part = new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/Sofia", timeZoneName: "shortOffset" })
    .formatToParts(new Date(`${date}T12:00:00Z`))
    .find((p) => p.type === "timeZoneName")?.value
  const m = /GMT([+-]\d+)/.exec(part ?? "")
  const h = m ? Number(m[1]) : 2
  return `${h >= 0 ? "+" : "-"}${String(Math.abs(h)).padStart(2, "0")}:00`
}

const at = (date: string, time: string) => new Date(`${date}T${time}:00${offsetFor(date)}`).getTime()
const sofiaDate = (ms: number) => new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Sofia" }).format(new Date(ms))
const sofiaTime = (ms: number) =>
  new Intl.DateTimeFormat("en-GB", { timeZone: "Europe/Sofia", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(new Date(ms))
const addDays = (date: string, days: number) => {
  const d = new Date(`${date}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}
const weekday = (date: string) => new Date(`${date}T12:00:00Z`).getUTCDay() // 0 = Sunday, 1 = Monday

// ---- Sample data (the same as the server's demo data) ----

const hoursWeek = (["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const).map((day) => ({
  day,
  start: "11:00",
  end: "23:00",
}))

const artists: Artist[] = [
  {
    id: "kai",
    name: "Kai",
    slug: "kai",
    style: "Bold traditional colour",
    bio: "Kai tattoos in the bold traditional Japanese style: heavy outlines, solid colour, and backgrounds of wind and water. Most of Kai's work is large: sleeves, backs and chest pieces.",
    hourlyRate: 90,
    dayRate: 550,
    signaturePiece: null,
    weeklyHours: hoursWeek,
    exceptions: [],
    averageRating: null,
    reviewCount: 0,
  },
  {
    id: "dario",
    name: "Dario",
    slug: "dario",
    style: "Black and grey, ink wash",
    bio: "Dario works only in black and grey: masks, demons and warriors, shaded like ink on paper.",
    hourlyRate: 80,
    dayRate: 500,
    signaturePiece: null,
    weeklyHours: hoursWeek,
    exceptions: [],
    averageRating: null,
    reviewCount: 0,
  },
  {
    id: "nika",
    name: "Nika",
    slug: "nika",
    style: "Fine line",
    bio: "Nika works with the finest needles: flowers, cranes and waves in thin black lines with small touches of red. A good choice for a first tattoo.",
    hourlyRate: 70,
    dayRate: 420,
    signaturePiece: null,
    weeklyHours: hoursWeek,
    exceptions: [],
    averageRating: null,
    reviewCount: 0,
  },
]

const sizeOf = (w: number, h: number): FlashSize => (Math.max(w, h) <= 8 ? "SMALL" : Math.max(w, h) <= 12 ? "MEDIUM" : "LARGE")
const design = (id: string, artistId: string, name: string, w: number, h: number, minutes: number, price: number): Flash => ({
  id,
  artistId,
  artistName: artists.find((a) => a.id === artistId)!.name,
  name,
  widthCm: w,
  heightCm: h,
  size: sizeOf(w, h),
  durationMinutes: minutes,
  price,
  deposit: 30,
  image: null,
  bookable: true,
})

const flash: Flash[] = [
  design("lucky-koi", "kai", "Lucky koi", 6, 8, 90, 140),
  design("koi-and-wave", "kai", "Koi and wave", 8, 12, 150, 220),
  design("small-dragon", "kai", "Small dragon", 6, 12, 120, 180),
  design("tiger-head", "kai", "Tiger head", 10, 10, 180, 260),
  design("hannya-mask", "dario", "Hannya mask", 8, 10, 150, 200),
  design("oni", "dario", "Oni", 7, 9, 120, 170),
  design("kabuto-helmet", "dario", "Kabuto helmet", 8, 8, 120, 170),
  design("katana-and-smoke", "dario", "Katana and smoke", 4, 16, 90, 130),
  design("crane-in-flight", "nika", "Crane in flight", 6, 8, 60, 90),
  design("peony", "nika", "Peony", 6, 6, 60, 90),
  design("cherry-blossom-branch", "nika", "Cherry blossom branch", 4, 14, 90, 110),
  design("small-wave", "nika", "Small wave", 5, 5, 60, 80),
]

const reviews: (Review & { approved: boolean })[] = [
  { id: "r1", artistId: "dario", name: "Elena", rating: 5, text: "Careful, clear and beautifully done.", createdAt: "2026-10-28T10:00:00Z", approved: true },
  { id: "r2", artistId: "nika", name: "Sofia", rating: 4, text: "A calm studio and excellent line work.", createdAt: "2026-10-20T10:00:00Z", approved: true },
  { id: "r3", artistId: "kai", name: "Petar", rating: 5, text: "The colour healed bright and solid.", createdAt: "2026-10-12T10:00:00Z", approved: true },
]

const bookings: Stored[] = []
let codeCounter = 0

function newCode() {
  const letters = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
  let code = ""
  for (let i = 0; i < 6; i++) code += letters[Math.floor(Math.random() * letters.length)]
  codeCounter++
  return `${code.slice(0, 4)}-${code.slice(4)}`
}

const normalizeCode = (code: string) => {
  const bare = code.trim().toUpperCase().replace(/[\s-]/g, "")
  return bare.length === 6 ? `${bare.slice(0, 4)}-${bare.slice(4)}` : bare
}

function normalizePhone(value: string): string | null {
  let s = value.trim().replace(/[\s\-().\\/]/g, "")
  if (s.startsWith("00")) s = "+" + s.slice(2)
  else if (s.startsWith("0")) s = "+359" + s.slice(1)
  else if (s.startsWith("359")) s = "+" + s
  else if (!s.startsWith("+")) s = "+359" + s
  return /^\+[1-9]\d{7,14}$/.test(s) ? s : null
}

const mask = (phone: string) => phone.slice(0, 4) + "*".repeat(Math.max(0, phone.length - 7)) + phone.slice(-3)

// ---- Bookings ----

const holdsTime = (b: Stored, now = Date.now()) =>
  b.status !== "CANCELLED" && b.status !== "EXPIRED" && !(b.status === "PENDING" && b.deposit.dueAt && new Date(b.deposit.dueAt).getTime() <= now)

const slotsFor = (start: number, end: number) => {
  const out: number[] = []
  for (let t = start; t < end + 30 * MIN; t += 30 * MIN) out.push(t)
  return out
}

function taken(artistId: string) {
  const set = new Set<number>()
  bookings.filter((b) => b.artistId === artistId && holdsTime(b)).forEach((b) => b.slots.forEach((s) => set.add(s)))
  return set
}

function freeStarts(artistId: string, date: string, minutes: number) {
  const today = sofiaDate(Date.now())
  if (date < today || date > addDays(today, 30) || weekday(date) === 1) return []
  const busy = taken(artistId)
  const earliest = Date.now() + 120 * MIN
  const out: number[] = []
  for (let m = 11 * 60; m + minutes <= 23 * 60; m += 30) {
    const time = `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`
    const start = at(date, time)
    if (start < earliest) continue
    if (slotsFor(start, start + minutes * MIN).some((s) => busy.has(s))) continue
    out.push(start)
  }
  return out
}

function claimed(flashId: string) {
  return bookings.some((b) => b.flashId === flashId && holdsTime(b) && b.status !== "NO_SHOW")
}

function toDto(b: Stored): Booking {
  const now = Date.now()
  const active = b.status === "PENDING" || b.status === "BOOKED"
  const starts = new Date(b.startsAt).getTime()
  const refundDeadline = b.deposit.amount === null ? null : new Date(starts - 48 * 60 * MIN).toISOString()
  return {
    ...b,
    artistName: artists.find((a) => a.id === b.artistId)?.name ?? null,
    phoneMasked: mask(b.phone),
    cancellable: active && starts > now,
    refundDeadline,
    refundIfCancelledNow: b.deposit.status === "PAID" && refundDeadline !== null && now <= new Date(refundDeadline).getTime(),
    canPayDeposit: b.deposit.status === "WAITING" && active && starts > now && holdsTime(b),
    canReview: b.type !== "CONSULTATION" && b.status === "DONE" && !b.reviewed,
  }
}

function makeBooking(input: {
  type: BookingType
  status: BookingStatus
  artistId: string
  date: string
  time: string
  minutes: number
  title: string | null
  flashId?: string | null
  price: number | null
  deposit: number | null
  depositStatus: DepositStatus
  customerName: string
  phone: string
  email: string
  userId: string | null
  consultation?: { idea: string; placement: Placement; sizeCm: number | null; colour: ColourChoice; photos: Image[] } | null
}): Stored {
  const start = at(input.date, input.time)
  const end = start + input.minutes * MIN
  const f = input.flashId ? flash.find((d) => d.id === input.flashId) : null
  const b: Stored = {
    code: newCode(),
    type: input.type,
    status: input.status,
    title: input.title,
    flashId: input.flashId ?? null,
    widthCm: f?.widthCm ?? null,
    heightCm: f?.heightCm ?? null,
    artistId: input.artistId,
    artistName: null,
    date: input.date,
    time: input.time,
    endTime: sofiaTime(end),
    startsAt: new Date(start).toISOString(),
    endsAt: new Date(end).toISOString(),
    price: input.price,
    deposit: {
      amount: input.deposit,
      status: input.depositStatus,
      method: input.depositStatus === "PAID" ? "ONLINE" : null,
      dueAt: input.depositStatus === "WAITING" ? new Date(Date.now() + (input.type === "FLASH" ? 15 : 72 * 60) * MIN).toISOString() : null,
      paidAt: input.depositStatus === "PAID" ? new Date().toISOString() : null,
      refundedAt: null,
    },
    leftToPay: input.price === null ? null : input.price - (input.deposit ?? 0),
    customerName: input.customerName,
    phoneMasked: null,
    consultation: input.consultation ?? null,
    cancellable: false,
    refundDeadline: null,
    refundIfCancelledNow: false,
    canPayDeposit: false,
    canReview: false,
    phone: input.phone,
    email: input.email,
    userId: input.userId,
    slots: slotsFor(start, end),
    reviewed: false,
    createdAt: new Date().toISOString(),
  }
  bookings.push(b)
  return b
}

/** Mira's bookings, so My account has something to show. */
function seedDemoCustomer() {
  const today = sofiaDate(Date.now())
  const nextOpen = (from: number) => {
    let d = addDays(today, from)
    while (weekday(d) === 1) d = addDays(d, 1)
    return d
  }
  const mira = { customerName: "Mira Petrova", phone: "+359881234567", email: DEMO_EMAIL, userId: "mira" }
  makeBooking({ ...mira, type: "SESSION", status: "BOOKED", artistId: "dario", date: nextOpen(8), time: "11:00", minutes: 240,
    title: "Back piece, session 1 of 3", price: 480, deposit: 100, depositStatus: "WAITING" })
  makeBooking({ ...mira, type: "FLASH", status: "BOOKED", artistId: "kai", date: nextOpen(28), time: "14:00", minutes: 120,
    title: "Small dragon", flashId: "small-dragon", price: 180, deposit: 30, depositStatus: "PAID" })
  makeBooking({ ...mira, type: "CONSULTATION", status: "BOOKED", artistId: "nika", date: nextOpen(12), time: "12:30", minutes: 30,
    title: null, price: null, deposit: null, depositStatus: "NONE",
    consultation: { idea: "I would like a flowing koi sleeve with red maple leaves, mostly black line work with a small amount of colour near the wrist.",
      placement: "FULL_SLEEVE", sizeCm: null, colour: "NOT_SURE", photos: ["hero-landscape", "banner-sky", "hero-portrait"].map((name) => ({ id: `ref-${name}`, url: `/images/${name}.jpg`, thumbUrl: `/images/${name}.jpg` })) } })
  const peony = makeBooking({ ...mira, type: "FLASH", status: "DONE", artistId: "nika", date: addDays(today, -19), time: "13:00", minutes: 60,
    title: "Peony", flashId: "peony", price: 90, deposit: 30, depositStatus: "PAID" })
  peony.slots = []
  makeBooking({ ...mira, type: "SESSION", status: "DONE", artistId: "kai", date: addDays(today, -55), time: "11:00", minutes: 360,
    title: "Tiger sleeve, session 3 of 3", price: 550, deposit: 100, depositStatus: "PAID" }).slots = []

  // Other customers, so the artists' calendars and the admin's lists have something in them
  const guest = (customerName: string, phone: string, email: string) => ({ customerName, phone, email, userId: null })
  makeBooking({ ...guest("Elena Markova", "+359887654321", "elena@example.com"), type: "FLASH", status: "BOOKED", artistId: "kai",
    date: nextOpen(1), time: "11:00", minutes: 180, title: "Tiger head", flashId: "tiger-head", price: 260, deposit: 30, depositStatus: "PAID" })
  makeBooking({ ...guest("Petar Ivanov", "+359889001122", "petar@example.com"), type: "CONSULTATION", status: "BOOKED", artistId: "kai",
    date: nextOpen(2), time: "15:00", minutes: 30, title: null, price: null, deposit: null, depositStatus: "NONE",
    consultation: { idea: "Blackwork back piece with a dragon climbing through clouds.", placement: "BACK", sizeCm: 45, colour: "BLACK_AND_GREY",
      photos: ["footer-night", "hero-landscape"].map((name) => ({ id: `ref-petar-${name}`, url: `/images/${name}.jpg`, thumbUrl: `/images/${name}.jpg` })) } })
  makeBooking({ ...guest("Sofia Koleva", "+359885550199", "sofia@example.com"), type: "SESSION", status: "BOOKED", artistId: "kai",
    date: addDays(today, -1), time: "12:00", minutes: 180, title: "Koi sleeve, session 2 of 4", price: 360, deposit: 80, depositStatus: "PAID" }).slots = []
  makeBooking({ ...guest("Nikolay Petrov", "+359884443322", "nikolay@example.com"), type: "SESSION", status: "BOOKED", artistId: "dario",
    date: nextOpen(3), time: "12:00", minutes: 240, title: "Hannya forearm, session 1 of 2", price: 420, deposit: 100, depositStatus: "WAITING" })
}

// ---- Requests ----

type Answer = { status: number; body?: unknown }
const ok = (body: unknown, status = 200): Answer => ({ status, body })
const fail = (status: number, code: string, detail: string, errors?: Record<string, string>): Answer => ({
  status,
  body: { status, title: "Error", detail, code, ...(errors ? { errors } : {}) },
})

const EMAIL_TAKEN = "There's already an account with this email. Please sign in instead."
type MockUser = { id: string; name: string; email: string; phone: string; dateOfBirth: string; password: string; createdAt: string; active?: boolean }
type SignIn = { token: string; userId: string }
let signedIn: SignIn | null = null
const users = new Map<string, MockUser>([
  ["mira", { id: "mira", name: "Mira Petrova", email: DEMO_EMAIL, phone: "+359 88 123 4567", dateOfBirth: "1998-03-12", password: "", createdAt: "2026-09-01T10:00:00Z" }],
])

function login(userId: string) {
  const token = `mock-${userId}-${Date.now()}`
  signedIn = { token, userId }
  const u = users.get(userId)!
  return {
    token,
    expiresAt: new Date(Date.now() + 12 * 60 * MIN).toISOString(),
    user: { username: u.email, role: "USER", artistId: null, name: u.name },
  }
}

function customerOf(headers: Headers) {
  const token = headers.get("Authorization")?.replace("Bearer ", "")
  return signedIn && token === signedIn.token ? signedIn.userId : null
}

function withRatings(a: Artist): Artist {
  const own = reviews.filter((r) => r.approved && r.artistId === a.id)
  const avg = own.length ? Math.round((own.reduce((s, r) => s + r.rating, 0) / own.length) * 10) / 10 : null
  return { ...a, averageRating: avg, reviewCount: own.length }
}

const sheetDto = (f: Flash): Flash => ({ ...f, bookable: !claimed(f.id) && !hiddenFlash.includes(f.id) })

function findForCustomer(code: string, phone: string) {
  const p = normalizePhone(phone)
  return bookings.find((b) => b.code === normalizeCode(code) && b.phone === p) ?? null
}

function cancel(b: Stored): Answer {
  if (b.status !== "PENDING" && b.status !== "BOOKED") return fail(409, "BOOKING_NOT_ACTIVE", "This booking is no longer active.")
  if (new Date(b.startsAt).getTime() <= Date.now()) return fail(409, "CANCELLATION_CLOSED", "The appointment has already started. Please call the studio.")
  const dto = toDto(b)
  b.status = "CANCELLED"
  if (b.deposit.status === "WAITING") b.deposit.status = "NOT_PAID"
  else if (b.deposit.status === "PAID") {
    b.deposit.status = dto.refundIfCancelledNow ? "REFUNDED" : "KEPT"
    if (b.deposit.status === "REFUNDED") b.deposit.refundedAt = new Date().toISOString()
  }
  return ok(toDto(b))
}

function pay(b: Stored): Answer {
  if (b.deposit.status !== "WAITING") return fail(409, "NOTHING_TO_PAY", "The deposit is already paid.")
  if (!holdsTime(b)) return fail(409, "HOLD_EXPIRED", "The hold expired and the time is free again. Pick a time again to continue.")
  // The pretend payment page: paid at once
  b.status = "BOOKED"
  b.deposit = { ...b.deposit, status: "PAID", method: "ONLINE", paidAt: new Date().toISOString() }
  return ok({ checkoutUrl: `/booking/confirmed?code=${b.code}`, amount: b.deposit.amount, dueAt: b.deposit.dueAt })
}

function validDetails(d: Record<string, unknown>, deposit: boolean) {
  const errors: Record<string, string> = {}
  if (!String(d.customerName ?? "").trim()) errors.customerName = "must not be blank"
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(d.email ?? ""))) errors.email = "must be a well-formed email address"
  if (!d.adult) errors.adult = "Please confirm that you are 18 or older"
  if (!d.consentForm) errors.consentForm = "Please read and agree to the consent form"
  if (deposit && !d.depositPolicy) errors.depositPolicy = "Please confirm the deposit policy"
  return errors
}

function ageCheck(dateOfBirth: string, date: string): Answer | null {
  const [by, bm, bd] = dateOfBirth.split("-").map(Number)
  const [y, m, d] = date.split("-").map(Number)
  const age = y - by - (m < bm || (m === bm && d < bd) ? 1 : 0)
  return age < 18 ? fail(400, "UNDER_AGE", "You need to be 18 or older to get tattooed.") : null
}

function book(kind: "flash" | "consultation", d: Record<string, unknown>, userId: string | null, photos: Image[]): Answer {
  const f = kind === "flash" ? flash.find((x) => x.id === d.flashId) : null
  if (kind === "flash" && !f) return fail(404, "FLASH_NOT_FOUND", "This flash design doesn't exist (any more)")
  if (f && claimed(f.id)) return fail(409, "FLASH_NOT_AVAILABLE", "Sorry, this design was just booked by someone else. Every flash design is tattooed once.")
  const artistId = f ? f.artistId : String(d.artistId)
  if (!artists.some((a) => a.id === artistId)) return fail(404, "ARTIST_NOT_FOUND", "Artist not found")
  const errors = validDetails(d, kind === "flash")
  if (Object.keys(errors).length) return fail(400, "VALIDATION_FAILED", "Please check the highlighted fields", errors)
  const phone = normalizePhone(String(d.phone ?? ""))
  if (!phone) return fail(400, "INVALID_PHONE", "Please enter a valid phone number")
  const tooYoung = ageCheck(String(d.dateOfBirth), String(d.date))
  if (tooYoung) return tooYoung
  const minutes = f ? f.durationMinutes : 30
  const start = at(String(d.date), String(d.time))
  if (!freeStarts(artistId, String(d.date), minutes).includes(start)) {
    return fail(409, "SLOT_UNAVAILABLE", "Sorry, that time was just taken. Please pick another time.")
  }
  const b = makeBooking({
    type: kind === "flash" ? "FLASH" : "CONSULTATION",
    status: kind === "flash" ? "PENDING" : "BOOKED",
    artistId,
    date: String(d.date),
    time: String(d.time),
    minutes,
    title: f ? f.name : null,
    flashId: f?.id ?? null,
    price: f ? f.price : null,
    deposit: f ? 30 : null,
    depositStatus: f ? "WAITING" : "NONE",
    customerName: String(d.customerName).trim(),
    phone,
    email: String(d.email),
    userId,
    consultation:
      kind === "consultation"
        ? {
            idea: String(d.idea ?? ""),
            placement: d.placement as Placement,
            sizeCm: d.sizeCm ? Number(d.sizeCm) : null,
            colour: d.colour as ColourChoice,
            photos,
          }
        : null,
  })
  return ok(toDto(b), 201)
}

async function handle(method: string, url: URL, headers: Headers, body: BodyInit | null | undefined): Promise<Answer> {
  const path = url.pathname.replace(/^\/api/, "")
  const q = url.searchParams
  const json = async () => (typeof body === "string" ? (JSON.parse(body) as Record<string, unknown>) : {})
  const userId = customerOf(headers)
  if (path.startsWith("/staff/") || path.startsWith("/admin/")) return staffRoutes(method, path, q, headers, body)

  if (method === "GET" && path === "/artists") return ok(artists.filter((a) => artistExtra[a.id]?.active !== false).map(withRatings))
  let m = /^\/artists\/([\w-]+)(\/works)?$/.exec(path)
  if (method === "GET" && m) {
    const a = artists.find((x) => x.slug === m![1] || x.id === m![1])
    if (!a) return fail(404, "ARTIST_NOT_FOUND", "Artist not found")
    return ok(m[2] ? works.filter((w) => w.artistId === a.id) : withRatings(a))
  }
  if (method === "GET" && path === "/flash") {
    return ok(
      flash
        .filter((f) => !claimed(f.id) && !hiddenFlash.includes(f.id) && artistExtra[f.artistId]?.active !== false)
        .filter((f) => !q.get("artistId") || f.artistId === q.get("artistId"))
        .filter((f) => !q.get("size") || f.size === q.get("size"))
        .map(sheetDto),
    )
  }
  m = /^\/flash\/([\w-]+)$/.exec(path)
  if (method === "GET" && m) {
    const f = flash.find((x) => x.id === m![1])
    return f ? ok(sheetDto(f)) : fail(404, "FLASH_NOT_FOUND", "This flash design doesn't exist (any more)")
  }
  if (method === "GET" && path === "/reviews") {
    const list = reviews.filter((r) => r.approved && (!q.get("artistId") || r.artistId === q.get("artistId")))
    const avg = list.length ? Math.round((list.reduce((s, r) => s + r.rating, 0) / list.length) * 10) / 10 : 0
    return ok({ averageRating: avg, count: list.length, reviews: list })
  }
  if (method === "GET" && path === "/availability") {
    const f = q.get("flashId") ? flash.find((x) => x.id === q.get("flashId")) : null
    const artistId = f ? f.artistId : q.get("artistId")
    if (!artistId) return fail(400, "VALIDATION_FAILED", "Give a flashId or an artistId")
    const minutes = f ? f.durationMinutes : 30
    const from = q.get("from") ?? sofiaDate(Date.now())
    const days = Number(q.get("days") ?? 14)
    const result: Availability = {
      artistId,
      flashId: f?.id ?? null,
      durationMinutes: minutes,
      days: Array.from({ length: days }, (_, i) => {
        const date = addDays(from, i)
        return {
          date,
          closed: weekday(date) === 1,
          slots: freeStarts(artistId, date, minutes).map((t) => ({ time: sofiaTime(t), startsAt: new Date(t).toISOString() })),
        }
      }),
    }
    return ok(result)
  }
  if (method === "POST" && path === "/bookings/flash") return book("flash", await json(), userId, [])
  if (method === "POST" && path === "/bookings/consultation" && body instanceof FormData) {
    const part = body.get("booking")
    const data = JSON.parse(part instanceof Blob ? await part.text() : String(part)) as Record<string, unknown>
    const files = body.getAll("photos").filter((p): p is File => p instanceof File)
    if (files.length > 5) return fail(400, "INVALID_FILE", "You can add up to 5 photos")
    return book("consultation", data, userId, await Promise.all(files.map(toImage)))
  }
  if (method === "POST" && ["/bookings/lookup", "/bookings/cancel", "/bookings/deposit", "/bookings/review"].includes(path)) {
    const data = await json()
    const b = findForCustomer(String(data.code ?? ""), String(data.phone ?? ""))
    if (!b) return fail(404, "BOOKING_NOT_FOUND", "No booking matches this code and phone number. Check both and try again.")
    if (path === "/bookings/lookup") return ok(toDto(b))
    if (path === "/bookings/cancel") return cancel(b)
    if (path === "/bookings/deposit") return pay(b)
    return review(b, data)
  }
  if (method === "POST" && path === "/messages") {
    const part = body instanceof FormData ? body.get("message") : null
    const d = part instanceof Blob ? (JSON.parse(await part.text()) as Record<string, unknown>) : await json()
    const photos = body instanceof FormData ? await Promise.all(body.getAll("photos").filter((f): f is File => f instanceof File).map(toImage)) : []
    const contact = String(d.contact ?? "").trim()
    const earlier = messages.filter((x) => x.contact.toLowerCase() === contact.toLowerCase()).length
    messages.push({ id: `message-${Date.now()}`, name: String(d.name ?? "").trim(), contact, text: String(d.text ?? "").trim(), photos,
      read: false, readAt: null, createdAt: new Date().toISOString(), senderMessageNumber: earlier + 1 })
    return ok(undefined, 201)
  }

  // ---- Sign in and My account ----
  if (method === "POST" && path === "/auth/login") {
    const data = await json()
    const staffUser = staffUsers.find((u) => u.username === String(data.username ?? "").trim().toLowerCase())
    if (staffUser && staffUser.active && String(data.password ?? "").length >= 8) return ok(staffLogin(staffUser))
    const user = [...users.values()].find((u) => u.email === String(data.username ?? "").trim().toLowerCase())
    if (!user || user.active === false || String(data.password ?? "").length < 8 || (user.password && user.password !== data.password)) {
      return fail(401, "INVALID_LOGIN", "Wrong email or password")
    }
    return ok(login(user.id))
  }
  if (method === "POST" && path === "/auth/register") {
    const data = await json()
    const email = String(data.email ?? "").trim().toLowerCase()
    if ([...users.values()].some((u) => u.email === email)) return fail(409, "CONFLICT", EMAIL_TAKEN)
    const today = sofiaDate(Date.now())
    const tooYoung = ageCheck(String(data.dateOfBirth), today)
    if (tooYoung) return fail(400, "UNDER_AGE", "You need to be 18 or older to create an account.")
    const id = `user-${users.size + 1}`
    users.set(id, { id, name: String(data.name), email, phone: String(data.phone), dateOfBirth: String(data.dateOfBirth),
      password: String(data.password), createdAt: new Date().toISOString() })
    return ok(login(id), 201)
  }
  if (path.startsWith("/account")) {
    if (!userId) return fail(401, "UNAUTHORIZED", "Please sign in again.")
    const user = users.get(userId)!
    if (method === "GET" && path === "/account") return ok({ name: user.name, email: user.email, phone: user.phone, dateOfBirth: user.dateOfBirth, createdAt: user.createdAt })
    if (method === "PUT" && path === "/account") {
      const data = await json()
      const email = String(data.email ?? "").trim().toLowerCase()
      if (!normalizePhone(String(data.phone ?? ""))) return fail(400, "INVALID_PHONE", "Please enter a valid phone number")
      if (email !== user.email) {
        const given = String(data.password ?? "")
        if (!given) return fail(400, "VALIDATION_FAILED", "Enter your password to change your email.")
        if (given.length < 8 || (user.password && user.password !== given)) return fail(400, "VALIDATION_FAILED", "Your current password is not right.")
      }
      if ([...users.values()].some((u) => u.id !== userId && u.email === email)) return fail(409, "CONFLICT", EMAIL_TAKEN)
      Object.assign(user, { name: String(data.name ?? "").trim(), phone: String(data.phone ?? "").trim(), email })
      return ok(login(userId))
    }
    if (method === "GET" && path === "/account/bookings") {
      const now = Date.now()
      const when = q.get("when") ?? "UPCOMING"
      let list = bookings.filter((b) => b.userId === userId)
      if (when === "UPCOMING") list = list.filter((b) => (b.status === "PENDING" || b.status === "BOOKED") && new Date(b.endsAt).getTime() > now)
      if (when === "PAST") list = list.filter((b) => !((b.status === "PENDING" || b.status === "BOOKED") && new Date(b.endsAt).getTime() > now))
      if (q.get("type")) list = list.filter((b) => b.type === q.get("type"))
      list.sort((a, b) => (when === "UPCOMING" ? 1 : -1) * a.startsAt.localeCompare(b.startsAt))
      return ok({ items: list.map(toDto), page: 0, size: list.length, totalItems: list.length, totalPages: 1 })
    }
    m = /^\/account\/bookings\/([\w-]+)\/(cancel|deposit|review)$/.exec(path)
    if (method === "POST" && m) {
      const b = bookings.find((x) => x.code === normalizeCode(m![1]) && x.userId === userId)
      if (!b) return fail(404, "BOOKING_NOT_FOUND", "This booking isn't in your account")
      if (m[2] === "cancel") return cancel(b)
      if (m[2] === "deposit") return pay(b)
      return review(b, await json())
    }
    if (method === "PUT" && path === "/account/password") return ok(login(userId))
    if (method === "POST" && path === "/account/delete") {
      users.delete(userId)
      signedIn = null
      return ok(undefined, 204)
    }
  }
  return fail(404, "NOT_FOUND", "Not found (the sample data doesn't cover this)")
}

function review(b: Stored, data: Record<string, unknown>): Answer {
  if (b.type === "CONSULTATION" || b.status !== "DONE" || b.reviewed) {
    return fail(409, "REVIEW_NOT_ALLOWED", "Reviews are for finished tattoo appointments.")
  }
  b.reviewed = true
  reviews.push({ id: `r${reviews.length + 1}`, artistId: b.artistId, name: (b.customerName ?? "Client").split(" ")[0],
    rating: Number(data.rating), text: String(data.text ?? ""), createdAt: new Date().toISOString(), approved: false })
  return ok(undefined, 201)
}

// ---- The artist and admin area (/staff/**, /admin/**) ----
// Staff sign in with their username and any password of 8+ characters: admin, kai, dario, nika.

type StaffUser = {
  id: string
  username: string
  role: "ADMIN" | "ARTIST"
  artistId: string | null
  name: string | null
  active: boolean
  createdAt: string
  lastLoginAt: string | null
  loginCount: number
}
const SEEDED = "2026-09-01T10:00:00Z"
const staffUsers: StaffUser[] = [
  { id: "login-admin", username: "admin", role: "ADMIN", artistId: null, name: null, active: true, createdAt: SEEDED, lastLoginAt: null, loginCount: 0 },
  ...artists.map((a) => ({
    id: `login-${a.id}`,
    username: a.id,
    role: "ARTIST" as const,
    artistId: a.id,
    name: a.name,
    active: true,
    createdAt: SEEDED,
    lastLoginAt: null,
    loginCount: 0,
  })),
]
let staffSignedIn: SignIn | null = null
const artistExtra: Record<string, { breakMinutes: number; active: boolean; sortOrder: number }> = Object.fromEntries(
  artists.map((a, i) => [a.id, { breakMinutes: 30, active: true, sortOrder: i }]),
)
const hiddenFlash: string[] = []
const paintings = ["hero-portrait", "hero-landscape", "banner-sky", "footer-night"]
const picture = (name: string, i: number): Image => ({ id: `${name}-${i}`, url: `/images/${name}.jpg`, thumbUrl: `/images/${name}.jpg` })
const works: Work[] = artists.flatMap((a, ai) =>
  [0, 1].map((i) => ({
    id: `work-${a.id}-${i}`,
    artistId: a.id,
    image: picture(paintings[(ai + i) % paintings.length], i),
    caption: i === 0 ? `${a.name}'s sleeve` : "Back piece detail",
    createdAt: SEEDED,
  })),
)
const messages: (Message & { handledAt?: string })[] = [
  {
    id: "message-1",
    name: "Ivana",
    contact: "ivana@example.com",
    text: "Could this old shoulder tattoo be covered with a peony and dark leaves?",
    photos: [picture("hero-portrait", 9)],
    read: false,
    readAt: null,
    createdAt: new Date(Date.now() - 3 * 60 * MIN).toISOString(),
    senderMessageNumber: 1,
  },
  {
    id: "message-2",
    name: "Georgi",
    contact: "+359 88 420 1100",
    text: "I have a question about healing after my session with Kai.",
    photos: [],
    read: false,
    readAt: null,
    createdAt: new Date(Date.now() - 26 * 60 * MIN).toISOString(),
    senderMessageNumber: 2,
  },
]

/** A picture someone chose, kept as a data address so it survives reloads of the sample data. */
function toImage(file: File): Promise<Image> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve({ id: `upload-${Date.now()}-${Math.round(Math.random() * 1e6)}`, url: String(reader.result), thumbUrl: String(reader.result) })
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

function staffLogin(u: StaffUser) {
  const token = `mock-staff-${u.id}-${Date.now()}`
  staffSignedIn = { token, userId: u.id }
  u.lastLoginAt = new Date().toISOString()
  u.loginCount++
  return { token, expiresAt: new Date(Date.now() + 12 * 60 * MIN).toISOString(), user: { username: u.username, role: u.role, artistId: u.artistId, name: u.name } }
}

function staffOf(headers: Headers) {
  const token = headers.get("Authorization")?.replace("Bearer ", "")
  if (!staffSignedIn || token !== staffSignedIn.token) return null
  const u = staffUsers.find((x) => x.id === staffSignedIn!.userId)
  return u && u.active ? u : null
}

function page<T>(items: T[], q: URLSearchParams) {
  const size = Number(q.get("size") ?? 10)
  const number = Number(q.get("page") ?? 0)
  return { items: items.slice(number * size, number * size + size), page: number, size, totalItems: items.length, totalPages: Math.ceil(items.length / size) }
}

const nowMs = () => Date.now()
const statusNow = (b: Stored): BookingStatus => (b.status === "PENDING" && !holdsTime(b) ? "EXPIRED" : b.status)

function staffDto(b: Stored): StaffBooking {
  const status = statusNow(b)
  const dto = toDto(b)
  return {
    ...dto,
    status,
    id: b.code,
    phone: b.phone,
    email: b.email || null,
    dateOfBirth: null,
    notes: b.notes ?? null,
    depositOverdue: b.deposit.status === "WAITING" && !!b.deposit.dueAt && new Date(b.deposit.dueAt).getTime() <= nowMs(),
    followsBookingId: null,
    anonymized: false,
    awaitingStatus: status === "BOOKED" && new Date(b.endsAt).getTime() <= nowMs(),
    createdAt: b.createdAt ?? b.startsAt,
  }
}

function bookingList(q: URLSearchParams, artistId: string | null): Answer {
  const today = sofiaDate(nowMs())
  const from = q.get("from") ?? addDays(today, -7)
  const to = q.get("to") ?? addDays(today, 30)
  const text = (q.get("q") ?? "").toLowerCase()
  const list = bookings
    .filter((b) => (!artistId || b.artistId === artistId) && (!q.get("artistId") || b.artistId === q.get("artistId")))
    .filter((b) => b.date >= from && b.date <= to)
    .filter((b) => !q.get("status") || statusNow(b) === q.get("status"))
    .filter((b) => !q.get("type") || b.type === q.get("type"))
    .filter((b) => !q.get("depositStatus") || b.deposit.status === q.get("depositStatus"))
    .filter((b) => !q.get("depositMethod") || b.deposit.method === q.get("depositMethod"))
    .filter((b) => !text || [b.customerName, b.phone, b.email, b.code, b.title, b.consultation?.idea].some((v) => (v ?? "").toLowerCase().includes(text)))
    .sort((x, y) => x.startsAt.localeCompare(y.startsAt))
  return ok(page(list.map(staffDto), q))
}

function setStatus(b: Stored, status: string): Answer {
  const current = statusNow(b)
  if (status === "CANCELLED") {
    if (current !== "PENDING" && current !== "BOOKED") return fail(409, "BOOKING_NOT_ACTIVE", "Only held or booked appointments can be cancelled.")
    b.status = "CANCELLED"
    if (b.deposit.status === "WAITING") b.deposit.status = "NOT_PAID"
    if (b.deposit.status === "PAID") {
      b.deposit.status = "REFUNDED" // the studio cancelling always refunds
      b.deposit.refundedAt = new Date().toISOString()
    }
    return ok(staffDto(b))
  }
  if (status === "DONE" || status === "NO_SHOW") {
    if (new Date(b.startsAt).getTime() > nowMs()) return fail(409, "BOOKING_NOT_STARTED", "Mark it Done or No-show once the appointment has started.")
    if (!["BOOKED", "DONE", "NO_SHOW"].includes(current)) return fail(409, "BOOKING_NOT_ACTIVE", "This booking is no longer active.")
    b.status = status
    return ok(staffDto(b))
  }
  return fail(400, "VALIDATION_FAILED", "Unknown status")
}

/** Upcoming customer bookings (flash, consultations) outside the artist's hours. */
function conflictsOf(a: Artist) {
  return bookings
    .filter((b) => b.artistId === a.id && (b.type === "FLASH" || b.type === "CONSULTATION") && holdsTime(b) && (b.status === "PENDING" || b.status === "BOOKED"))
    .filter((b) => new Date(b.startsAt).getTime() > nowMs())
    .filter((b) => {
      const e = a.exceptions.find((x) => x.date === b.date)
      if (e?.closed) return true
      const day = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"][weekday(b.date)]
      const h = e ?? a.weeklyHours.find((w) => w.day === day)
      return !h || !h.start || !h.end || b.time < h.start || b.endTime > h.end
    })
    .map(staffDto)
}

const scheduleOf = (a: Artist) => ({
  weeklyHours: a.weeklyHours,
  exceptions: a.exceptions.filter((e) => e.date >= sofiaDate(nowMs())).sort((x, y) => x.date.localeCompare(y.date)),
  breakMinutes: artistExtra[a.id]?.breakMinutes ?? 30,
  conflicts: conflictsOf(a),
})

function adminFlashDto(f: Flash): AdminFlash {
  const b = bookings.find((x) => x.flashId === f.id && holdsTime(x) && x.status !== "NO_SHOW")
  const hidden = hiddenFlash.includes(f.id)
  return {
    id: f.id,
    artistId: f.artistId,
    artistName: artists.find((a) => a.id === f.artistId)?.name ?? f.artistName,
    name: f.name,
    widthCm: f.widthCm,
    heightCm: f.heightCm,
    size: f.size,
    durationMinutes: f.durationMinutes,
    price: f.price,
    image: f.image,
    available: !hidden,
    state: b ? (b.status === "PENDING" ? "HELD" : b.status === "DONE" ? "TATTOOED" : "BOOKED") : hidden ? "HIDDEN" : "ON_SHEET",
    bookingCode: b?.code ?? null,
    sortOrder: flash.indexOf(f),
    createdAt: SEEDED,
  }
}

function flashProblems(d: Record<string, unknown>) {
  const errors: Record<string, string> = {}
  const name = String(d.name ?? "").trim()
  if (!name || name.length > 60) errors.name = "must be 1 to 60 characters"
  for (const field of ["widthCm", "heightCm"]) {
    const v = Number(d[field])
    if (!Number.isInteger(v) || v < 1 || v > 60) errors[field] = "must be between 1 and 60"
  }
  const minutes = Number(d.durationMinutes)
  if (!Number.isInteger(minutes) || minutes < 30 || minutes > 720 || minutes % 30) errors.durationMinutes = "must be 30 to 720 minutes in steps of 30"
  const price = Number(d.price)
  if (!(price >= 1 && price <= 5000)) errors.price = "must be between 1 and 5000"
  return Object.keys(errors).length ? fail(400, "VALIDATION_FAILED", "Please check the highlighted fields", errors) : null
}

function applyFlash(f: Flash, d: Record<string, unknown>) {
  f.name = String(d.name).trim()
  f.widthCm = Number(d.widthCm)
  f.heightCm = Number(d.heightCm)
  f.size = sizeOf(f.widthCm, f.heightCm)
  f.durationMinutes = Number(d.durationMinutes)
  f.price = Number(d.price)
  if (d.available === false && !hiddenFlash.includes(f.id)) hiddenFlash.push(f.id)
  if (d.available === true && hiddenFlash.includes(f.id)) hiddenFlash.splice(hiddenFlash.indexOf(f.id), 1)
}

/** /staff/flash and /admin/flash: an artist works on their own designs, the admin on everyone's. */
async function flashRoutes(method: string, rest: string, q: URLSearchParams, body: BodyInit | null | undefined, ownArtist: string | null): Promise<Answer> {
  const json = async () => (typeof body === "string" ? (JSON.parse(body) as Record<string, unknown>) : {})
  if (method === "GET" && rest === "") {
    const text = (q.get("q") ?? "").toLowerCase()
    const list = flash
      .filter((f) => (!ownArtist || f.artistId === ownArtist) && (!q.get("artistId") || f.artistId === q.get("artistId")))
      .filter((f) => !q.get("available") || String(!hiddenFlash.includes(f.id)) === q.get("available"))
      .filter((f) => !text || f.name.toLowerCase().includes(text))
    return ok(page(list.map(adminFlashDto), q))
  }
  if (method === "POST" && rest === "" && body instanceof FormData) {
    const part = body.get("flash")
    const d = part instanceof Blob ? (JSON.parse(await part.text()) as Record<string, unknown>) : {}
    const artistId = ownArtist ?? String(d.artistId ?? "")
    const artist = artists.find((a) => a.id === artistId)
    if (!artist) return fail(404, "ARTIST_NOT_FOUND", "Artist not found")
    const problem = flashProblems(d)
    if (problem) return problem
    const id = `${String(d.name).trim().toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${Math.round(Math.random() * 1e4)}`
    const f = design(id, artistId, String(d.name), Number(d.widthCm), Number(d.heightCm), Number(d.durationMinutes), Number(d.price))
    applyFlash(f, d)
    const file = body.get("image")
    if (file instanceof File) f.image = await toImage(file)
    flash.push(f)
    return ok(adminFlashDto(f), 201)
  }
  const m = /^\/([\w-]+)(\/image)?$/.exec(rest)
  const f = m ? flash.find((x) => x.id === m[1] && (!ownArtist || x.artistId === ownArtist)) : null
  if (!f) return fail(404, "FLASH_NOT_FOUND", "This flash design doesn't exist (any more)")
  if (method === "PUT" && m![2] && body instanceof FormData) {
    const file = body.get("image")
    if (file instanceof File) f.image = await toImage(file)
    return ok(adminFlashDto(f))
  }
  if (method === "PUT") {
    const d = await json()
    const problem = flashProblems(d)
    if (problem) return problem
    applyFlash(f, d)
    return ok(adminFlashDto(f))
  }
  if (method === "PATCH") {
    applyFlash(f, { ...f, available: (await json()).active })
    return ok(adminFlashDto(f))
  }
  if (method === "DELETE") {
    if (claimed(f.id)) return fail(409, "CONFLICT", "This design is held or booked. Take it off the sheet instead.")
    flash.splice(flash.indexOf(f), 1)
    return ok(undefined, 204)
  }
  return fail(404, "NOT_FOUND", "Not found")
}

const adminArtistDto = (a: Artist): AdminArtist => ({
  id: a.id,
  name: a.name,
  slug: a.slug,
  style: a.style,
  bio: a.bio,
  hourlyRate: a.hourlyRate,
  dayRate: a.dayRate,
  signaturePiece: a.signaturePiece,
  weeklyHours: a.weeklyHours,
  exceptions: a.exceptions,
  breakMinutes: artistExtra[a.id]?.breakMinutes ?? 30,
  sortOrder: artistExtra[a.id]?.sortOrder ?? 0,
  active: artistExtra[a.id]?.active ?? true,
  username: staffUsers.find((u) => u.artistId === a.id)?.username ?? null,
  conflicts: conflictsOf(a),
})

const upcomingFor = (artistId: string) =>
  bookings.some((b) => b.artistId === artistId && (b.status === "PENDING" || b.status === "BOOKED") && holdsTime(b) && new Date(b.startsAt).getTime() > nowMs())

function adminUserDto(u: StaffUser | MockUser): AdminUser {
  if ("role" in u) {
    return { id: u.id, username: u.username, role: u.role, active: u.active, name: u.role === "ADMIN" ? u.name : null, email: null, phone: null,
      artistId: u.artistId, artistName: artists.find((a) => a.id === u.artistId)?.name ?? null, createdAt: u.createdAt, lastLoginAt: u.lastLoginAt, loginCount: u.loginCount }
  }
  return { id: u.id, username: u.email, role: "USER", active: u.active !== false, name: u.name, email: u.email, phone: u.phone,
    artistId: null, artistName: null, createdAt: u.createdAt, lastLoginAt: null, loginCount: 0 }
}

async function staffRoutes(method: string, path: string, q: URLSearchParams, headers: Headers, body: BodyInit | null | undefined): Promise<Answer> {
  const me = staffOf(headers)
  if (!me) return fail(401, "UNAUTHORIZED", "Please sign in again.")
  const json = async () => (typeof body === "string" ? (JSON.parse(body) as Record<string, unknown>) : {})
  const admin = path.startsWith("/admin/")
  if (admin !== (me.role === "ADMIN")) return fail(403, "FORBIDDEN", "This page is for the studio admin.")
  const artist = me.artistId ? artists.find((a) => a.id === me.artistId)! : null
  let m: RegExpExecArray | null

  // Bookings (an artist sees their own)
  if (method === "GET" && (path === "/staff/bookings" || path === "/admin/bookings")) return bookingList(q, artist?.id ?? null)
  m = /^\/(?:staff|admin)\/bookings\/([\w-]+)(\/deposit-cash)?$/.exec(path)
  if (m) {
    const b = bookings.find((x) => x.code === m![1] && (!artist || x.artistId === artist.id))
    if (!b) return fail(404, "BOOKING_NOT_FOUND", "Booking not found")
    if (method === "PATCH") return setStatus(b, String((await json()).status))
    if (admin && method === "POST" && m[2]) {
      if (b.deposit.status !== "WAITING") return fail(409, "NOTHING_TO_PAY", "There is no deposit waiting.")
      b.deposit = { ...b.deposit, status: "PAID", method: "CASH", paidAt: new Date().toISOString() }
      if (b.status === "PENDING") b.status = "BOOKED"
      return ok(staffDto(b))
    }
    if (admin && method === "PUT") {
      const d = await json()
      if (!String(d.customerName ?? "").trim() || !normalizePhone(String(d.phone ?? ""))) return fail(400, "VALIDATION_FAILED", "Enter a name and a valid phone number")
      b.customerName = String(d.customerName).trim()
      b.phone = normalizePhone(String(d.phone))!
      b.email = String(d.email ?? "")
      b.notes = d.notes ? String(d.notes) : null
      return ok(staffDto(b))
    }
    if (admin && method === "DELETE") {
      bookings.splice(bookings.indexOf(b), 1)
      return ok(undefined, 204)
    }
  }
  if (admin && method === "GET" && path === "/admin/stats") {
    const today = sofiaDate(nowMs())
    const monday = addDays(today, -((weekday(today) + 6) % 7))
    const from = q.get("from") ?? monday
    const to = q.get("to") ?? addDays(monday, 6)
    const inRange = bookings.filter((b) => b.date >= from && b.date <= to)
    const count = (list: Stored[], s: BookingStatus) => list.filter((b) => b.status === s).length
    const revenue = (list: Stored[]) =>
      list.reduce((sum, b) => sum + (b.status === "DONE" ? (b.price ?? 0) : 0) + (b.deposit.status === "KEPT" ? (b.deposit.amount ?? 0) : 0), 0)
    const confirmed = (list: Stored[]) => list.filter((b) => ["BOOKED", "DONE", "NO_SHOW"].includes(b.status)).length
    const perArtist = artists.map((a) => {
      const own = inRange.filter((b) => b.artistId === a.id)
      return { artistId: a.id, name: a.name, bookings: confirmed(own), done: count(own, "DONE"), noShows: count(own, "NO_SHOW"), revenue: revenue(own) }
    })
    const busiest = [...perArtist].sort((x, y) => y.bookings - x.bookings)[0]
    return ok({
      from, to, bookings: confirmed(inRange), done: count(inRange, "DONE"), noShows: count(inRange, "NO_SHOW"), cancelled: count(inRange, "CANCELLED"),
      revenue: revenue(inRange), busiestArtist: busiest && busiest.bookings ? { id: busiest.artistId, name: busiest.name, bookings: busiest.bookings } : null, perArtist,
    })
  }

  // The artist's own things
  if (artist) {
    if (method === "GET" && path === "/staff/customers") {
      const text = (q.get("q") ?? "").toLowerCase()
      const seen = new Map<string, Stored>()
      for (const b of [...bookings].sort((x, y) => y.startsAt.localeCompare(x.startsAt))) {
        if (b.artistId !== artist.id || (b.type !== "CONSULTATION" && b.type !== "SESSION") || seen.has(b.phone)) continue
        if (text && ![b.customerName, b.phone, b.email].some((v) => (v ?? "").toLowerCase().includes(text))) continue
        seen.set(b.phone, b)
      }
      return ok([...seen.values()].map((b) => ({ bookingId: b.code, code: b.code, name: b.customerName ?? "", phone: b.phone, email: b.email || null,
        lastVisit: b.date, about: b.consultation?.idea ?? b.title })))
    }
    if (method === "POST" && path === "/staff/sessions") {
      const d = await json()
      const from = bookings.find((b) => b.code === d.customerBookingId && b.artistId === artist.id)
      if (!from) return fail(404, "BOOKING_NOT_FOUND", "That customer isn't one of yours")
      const date = String(d.date ?? "")
      const start = String(d.start ?? "")
      const end = String(d.end ?? "")
      if (!date || date < sofiaDate(nowMs())) return fail(400, "VALIDATION_FAILED", "Please check the highlighted fields", { date: "must be today or later" })
      if (end <= start) return fail(400, "VALIDATION_FAILED", "Please check the highlighted fields", { end: "must be after the start" })
      const startMs = at(date, start)
      const endMs = at(date, end)
      if (startMs <= nowMs()) return fail(400, "VALIDATION_FAILED", "That time has already passed")
      const busy = taken(artist.id)
      if (slotsFor(startMs, endMs).some((s) => busy.has(s))) return fail(409, "SLOT_UNAVAILABLE", "That time overlaps another booking or the break after it.")
      const deposit = Number(d.deposit ?? 0)
      const b = makeBooking({
        type: "SESSION", status: "BOOKED", artistId: artist.id, date, time: start, minutes: (endMs - startMs) / MIN, title: String(d.description ?? "").trim(),
        price: Number(d.price), deposit: deposit > 0 ? deposit : null, depositStatus: deposit > 0 ? "WAITING" : "NONE",
        customerName: from.customerName ?? "", phone: from.phone, email: from.email, userId: from.userId,
      })
      return ok(staffDto(b), 201)
    }
    if (path === "/staff/schedule") {
      if (method === "PUT") {
        const d = await json()
        const weekly = (d.weeklyHours as WorkingHours[]) ?? []
        if (weekly.some((h) => h.end <= h.start)) return fail(400, "VALIDATION_FAILED", "Each day's end must be after its start")
        artist.weeklyHours = weekly
        artist.exceptions = ((d.exceptions as DayException[]) ?? []).filter((e) => e.date >= sofiaDate(nowMs()))
        if (d.breakMinutes !== undefined) artistExtra[artist.id].breakMinutes = Number(d.breakMinutes)
      }
      return ok(scheduleOf(artist))
    }
    if (path.startsWith("/staff/flash")) return flashRoutes(method, path.slice("/staff/flash".length), q, body, artist.id)
    if (method === "GET" && path === "/staff/works") return ok(works.filter((w) => w.artistId === artist.id))
    if (method === "POST" && path === "/staff/works" && body instanceof FormData) {
      const file = body.get("image")
      if (!(file instanceof File)) return fail(400, "INVALID_FILE", "Choose a photo")
      const w: Work = { id: `work-${Date.now()}`, artistId: artist.id, image: await toImage(file), caption: String(body.get("caption") ?? ""), createdAt: new Date().toISOString() }
      works.push(w)
      return ok(w, 201)
    }
    m = /^\/staff\/works\/([\w-]+)$/.exec(path)
    if (method === "DELETE" && m) {
      const i = works.findIndex((w) => w.id === m![1] && w.artistId === artist.id)
      if (i < 0) return fail(404, "NOT_FOUND", "Photo not found")
      works.splice(i, 1)
      return ok(undefined, 204)
    }
    if (method === "PUT" && path === "/staff/signature" && body instanceof FormData) {
      const file = body.get("image")
      if (!(file instanceof File)) return fail(400, "INVALID_FILE", "Choose a picture")
      artist.signaturePiece = await toImage(file)
      return ok(artist.signaturePiece)
    }
    if (method === "DELETE" && path === "/staff/signature") {
      artist.signaturePiece = null
      return ok(undefined, 204)
    }
    return fail(404, "NOT_FOUND", "Not found (the sample data doesn't cover this)")
  }

  // The admin
  if (path.startsWith("/admin/flash")) return flashRoutes(method, path.slice("/admin/flash".length), q, body, null)
  if (method === "GET" && path === "/admin/artists/options") {
    return ok(artists.map((a) => ({ id: a.id, name: a.name, active: artistExtra[a.id]?.active ?? true, username: staffUsers.find((u) => u.artistId === a.id)?.username ?? null })))
  }
  if (method === "GET" && path === "/admin/artists") return ok(page([...artists].sort((x, y) => artistExtra[x.id].sortOrder - artistExtra[y.id].sortOrder).map(adminArtistDto), q))
  if ((method === "POST" && path === "/admin/artists") || (method === "PUT" && (m = /^\/admin\/artists\/([\w-]+)$/.exec(path)))) {
    const d = await json()
    const name = String(d.name ?? "").trim()
    if (!name) return fail(400, "VALIDATION_FAILED", "Please check the highlighted fields", { name: "must not be blank" })
    const existing = method === "PUT" ? artists.find((a) => a.id === m![1]) : null
    if (method === "PUT" && !existing) return fail(404, "ARTIST_NOT_FOUND", "Artist not found")
    const slug = String(d.slug || existing?.slug || name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || `artist-${Date.now()}`)
    if (artists.some((a) => a.slug === slug && a !== existing)) return fail(409, "CONFLICT", "Another artist already has this page address.")
    if (existing && d.active === false && artistExtra[existing.id].active && upcomingFor(existing.id))
      return fail(409, "CONFLICT", `${existing.name} has upcoming bookings. Cancel or move them first.`)
    const a: Artist = existing ?? { id: slug, name, slug, style: "", bio: "", hourlyRate: null, dayRate: null, signaturePiece: null, weeklyHours: [], exceptions: [], averageRating: null, reviewCount: 0 }
    Object.assign(a, {
      name, slug, style: String(d.style ?? ""), bio: String(d.bio ?? ""),
      hourlyRate: d.hourlyRate == null ? null : Number(d.hourlyRate), dayRate: d.dayRate == null ? null : Number(d.dayRate),
      weeklyHours: (d.weeklyHours as WorkingHours[]) ?? a.weeklyHours, exceptions: (d.exceptions as DayException[]) ?? a.exceptions,
    })
    if (!existing) {
      artists.push(a)
      artistExtra[a.id] = { breakMinutes: 30, active: true, sortOrder: artists.length }
    }
    if (d.breakMinutes != null) artistExtra[a.id].breakMinutes = Number(d.breakMinutes)
    if (typeof d.active === "boolean") artistExtra[a.id].active = d.active
    return ok(adminArtistDto(a), existing ? 200 : 201)
  }
  m = /^\/admin\/artists\/([\w-]+)(\/signature|\/login)?$/.exec(path)
  if (m) {
    const a = artists.find((x) => x.id === m![1])
    if (!a) return fail(404, "ARTIST_NOT_FOUND", "Artist not found")
    if (method === "PATCH") {
      const active = Boolean((await json()).active)
      if (!active && upcomingFor(a.id)) return fail(409, "CONFLICT", `${a.name} has upcoming bookings. Cancel or move them first.`)
      artistExtra[a.id].active = active
      staffUsers.filter((u) => u.artistId === a.id).forEach((u) => (u.active = active))
      return ok(adminArtistDto(a))
    }
    if (m[2] === "/signature" && method === "PUT" && body instanceof FormData) {
      const file = body.get("image")
      if (!(file instanceof File)) return fail(400, "INVALID_FILE", "Choose a picture")
      a.signaturePiece = await toImage(file)
      return ok(a.signaturePiece)
    }
    if (m[2] === "/signature" && method === "DELETE") {
      a.signaturePiece = null
      return ok(undefined, 204)
    }
    if (m[2] === "/login" && method === "POST") {
      const d = await json()
      const username = String(d.username ?? "").trim().toLowerCase()
      if (!/^[a-z0-9._-]{3,30}$/.test(username)) return fail(400, "VALIDATION_FAILED", "Please check the highlighted fields", { username: "3-30 letters, digits, dots, dashes or underscores" })
      if (String(d.password ?? "").length < 8) return fail(400, "VALIDATION_FAILED", "Please check the highlighted fields", { password: "must be at least 8 characters" })
      const own = staffUsers.find((u) => u.artistId === a.id)
      if (staffUsers.some((u) => u.username === username && u !== own)) return fail(409, "CONFLICT", "That username is taken.")
      if (own) own.username = username
      else staffUsers.push({ id: `login-${Date.now()}`, username, role: "ARTIST", artistId: a.id, name: a.name, active: true, createdAt: new Date().toISOString(), lastLoginAt: null, loginCount: 0 })
      return ok({ username }, 201)
    }
    if (!m[2] && method === "DELETE") {
      if (bookings.some((b) => b.artistId === a.id)) return fail(409, "CONFLICT", `${a.name} has bookings. Switch them off instead.`)
      artists.splice(artists.indexOf(a), 1)
      for (let i = flash.length - 1; i >= 0; i--) if (flash[i].artistId === a.id) flash.splice(i, 1)
      for (let i = works.length - 1; i >= 0; i--) if (works[i].artistId === a.id) works.splice(i, 1)
      for (let i = staffUsers.length - 1; i >= 0; i--) if (staffUsers[i].artistId === a.id) staffUsers.splice(i, 1)
      return ok(undefined, 204)
    }
  }
  if (method === "GET" && path === "/admin/reviews") {
    const list = reviews
      .filter((r) => !q.get("status") || r.approved === (q.get("status") === "approved"))
      .filter((r) => !q.get("artistId") || r.artistId === q.get("artistId"))
      .filter((r) => !q.get("minRating") || r.rating >= Number(q.get("minRating")))
      .sort((x, y) => y.createdAt.localeCompare(x.createdAt))
      .map((r) => ({ ...r, bookingId: null }))
    return ok(page(list, q))
  }
  m = /^\/admin\/reviews\/([\w-]+)$/.exec(path)
  if (m) {
    const r = reviews.find((x) => x.id === m![1])
    if (!r) return fail(404, "NOT_FOUND", "Review not found")
    if (method === "PATCH") r.approved = Boolean((await json()).approved)
    if (method === "PUT") Object.assign(r, (({ name, rating, text, approved }) => ({ name, rating, text, approved }))(await json()))
    if (method === "DELETE") {
      reviews.splice(reviews.indexOf(r), 1)
      return ok(undefined, 204)
    }
    return ok({ ...r, bookingId: null })
  }
  if (method === "GET" && path === "/admin/messages") {
    const text = (q.get("q") ?? "").toLowerCase()
    const list = messages
      .filter((x) => !q.get("read") || String(x.read) === q.get("read"))
      .filter((x) => !text || [x.name, x.contact, x.text].some((v) => v.toLowerCase().includes(text)))
      .sort((x, y) => y.createdAt.localeCompare(x.createdAt))
    return ok(page(list, q))
  }
  m = /^\/admin\/messages\/([\w-]+)$/.exec(path)
  if (m) {
    const x = messages.find((y) => y.id === m![1])
    if (!x) return fail(404, "NOT_FOUND", "Message not found")
    if (method === "PATCH") {
      x.read = Boolean((await json()).read)
      x.readAt = x.read ? new Date().toISOString() : null
    }
    if (method === "DELETE") {
      messages.splice(messages.indexOf(x), 1)
      return ok(undefined, 204)
    }
    return ok(x)
  }
  if (method === "GET" && path === "/admin/users") {
    const text = (q.get("q") ?? "").toLowerCase()
    const list = [...staffUsers.map(adminUserDto), ...[...users.values()].map(adminUserDto)]
      .filter((u) => !q.get("role") || u.role === q.get("role"))
      .filter((u) => !text || [u.username, u.name, u.email, u.phone].some((v) => (v ?? "").toLowerCase().includes(text)))
      .sort((x, y) => ["ADMIN", "ARTIST", "USER"].indexOf(x.role) - ["ADMIN", "ARTIST", "USER"].indexOf(y.role))
    return ok(page(list, q))
  }
  if (method === "POST" && path === "/admin/users") {
    const d = await json()
    if (String(d.password ?? "").length < 8) return fail(400, "VALIDATION_FAILED", "Set a password of at least 8 characters")
    if (d.role === "USER") {
      const email = String(d.email ?? "").trim().toLowerCase()
      if (!d.name || !normalizePhone(String(d.phone ?? "")) || !email) return fail(400, "VALIDATION_FAILED", "Customers need a name, an email and a phone number")
      if ([...users.values()].some((u) => u.email === email)) return fail(409, "CONFLICT", EMAIL_TAKEN)
      const id = `user-${users.size + 1}`
      users.set(id, { id, name: String(d.name).trim(), email, phone: String(d.phone).trim(), dateOfBirth: "", password: String(d.password), createdAt: new Date().toISOString() })
      return ok(adminUserDto(users.get(id)!), 201)
    }
    const username = String(d.username ?? "").trim().toLowerCase()
    if (!/^[a-z0-9._-]{3,30}$/.test(username)) return fail(400, "VALIDATION_FAILED", "Staff usernames are 3-30 letters, digits, dots, dashes or underscores")
    if (staffUsers.some((u) => u.username === username)) return fail(409, "CONFLICT", "That username is taken.")
    if (d.role === "ARTIST" && staffUsers.some((u) => u.artistId === d.artistId)) return fail(409, "CONFLICT", "That artist already has a login.")
    const u: StaffUser = { id: `login-${Date.now()}`, username, role: d.role === "ARTIST" ? "ARTIST" : "ADMIN", artistId: d.role === "ARTIST" ? String(d.artistId) : null,
      name: d.role === "ARTIST" ? (artists.find((a) => a.id === d.artistId)?.name ?? null) : null, active: true, createdAt: new Date().toISOString(), lastLoginAt: null, loginCount: 0 }
    staffUsers.push(u)
    return ok(adminUserDto(u), 201)
  }
  m = /^\/admin\/users\/([\w-]+)(\/password)?$/.exec(path)
  if (m) {
    const staffUser = staffUsers.find((u) => u.id === m![1])
    const customer = users.get(m[1])
    if (!staffUser && !customer) return fail(404, "NOT_FOUND", "User not found")
    if (m[2] && method === "PUT") return ok(undefined, 204)
    const self = staffUser === me
    if (method === "DELETE") {
      if (self) return fail(409, "CONFLICT", "You can't delete your own login.")
      if (staffUser) staffUsers.splice(staffUsers.indexOf(staffUser), 1)
      else users.delete(m[1])
      return ok(undefined, 204)
    }
    if (method === "PUT") {
      const d = await json()
      if (customer) {
        if (d.role !== "USER") return fail(409, "CONFLICT", "Customers stay customers here. Create a staff login instead.")
        if (!d.name || !normalizePhone(String(d.phone ?? ""))) return fail(400, "VALIDATION_FAILED", "Customers need a name and a phone number")
        Object.assign(customer, { name: String(d.name).trim(), phone: String(d.phone).trim(), active: d.active !== false })
        return ok(adminUserDto(customer))
      }
      const u = staffUser!
      if (self && (d.role !== u.role || d.active === false)) return fail(409, "CONFLICT", "You can't change or block your own login. Ask another admin.")
      if (d.role === "USER") return fail(409, "CONFLICT", "Staff logins have no customer details. Create a customer account instead.")
      u.role = d.role === "ARTIST" ? "ARTIST" : "ADMIN"
      u.artistId = u.role === "ARTIST" ? String(d.artistId) : null
      u.name = u.role === "ARTIST" ? (artists.find((a) => a.id === u.artistId)?.name ?? null) : u.name
      if (typeof d.active === "boolean") u.active = d.active
      return ok(adminUserDto(u))
    }
  }
  return fail(404, "NOT_FOUND", "Not found (the sample data doesn't cover this)")
}

// ---- Keeping the sample data ----

// The data lives in this tab's sessionStorage, so a reload or the payment page's redirect (a full
// page load, like a real payment provider's) still finds the bookings and the signed-in account.
const STATE_KEY = "blade-and-brush-sample-data"
type SavedState = {
  bookings: Stored[]
  reviews: typeof reviews
  users: [string, MockUser][]
  signedIn: SignIn | null
  staff?: {
    users: StaffUser[]
    signedIn: SignIn | null
    artists: Artist[]
    artistExtra: typeof artistExtra
    flash: Flash[]
    hiddenFlash: string[]
    works: Work[]
    messages: Message[]
  }
}

function restoreState(): boolean {
  try {
    const raw = sessionStorage.getItem(STATE_KEY)
    if (!raw) return false
    const saved = JSON.parse(raw) as SavedState
    bookings.splice(0, bookings.length, ...saved.bookings)
    reviews.splice(0, reviews.length, ...saved.reviews)
    users.clear()
    for (const [id, user] of saved.users) users.set(id, user)
    signedIn = saved.signedIn
    if (saved.staff) {
      const s = saved.staff
      staffUsers.splice(0, staffUsers.length, ...s.users)
      staffSignedIn = s.signedIn
      artists.splice(0, artists.length, ...s.artists)
      Object.assign(artistExtra, s.artistExtra)
      flash.splice(0, flash.length, ...s.flash)
      hiddenFlash.splice(0, hiddenFlash.length, ...s.hiddenFlash)
      works.splice(0, works.length, ...s.works)
      messages.splice(0, messages.length, ...s.messages)
    }
    return true
  } catch {
    return false
  }
}

function saveState() {
  const state: SavedState = {
    bookings,
    reviews,
    users: [...users],
    signedIn,
    staff: { users: staffUsers, signedIn: staffSignedIn, artists, artistExtra, flash, hiddenFlash, works, messages },
  }
  try {
    sessionStorage.setItem(STATE_KEY, JSON.stringify(state))
  } catch {
    // Too many uploaded pictures for this tab's storage: the newest changes last until a reload
  }
}

export function installMockApi() {
  if (!restoreState()) {
    seedDemoCustomer()
    saveState()
  }
  const realFetch = window.fetch.bind(window)
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === "string" ? input : input instanceof URL ? input.href : input.url, window.location.origin)
    if (!url.pathname.startsWith("/api/")) return realFetch(input, init)
    await new Promise((resolve) => setTimeout(resolve, 150)) // a little like a real network
    const answer = await handle((init?.method ?? "GET").toUpperCase(), url, new Headers(init?.headers), init?.body)
    saveState()
    return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), {
      status: answer.status,
      headers: { "Content-Type": "application/json" },
    })
  }
  console.info(`Sample data instead of the API (npm run dev:mock). Customers sign in as ${DEMO_EMAIL}, staff as admin, kai, dario or nika, with any password of 8+ characters.`)
  void codeCounter
}
