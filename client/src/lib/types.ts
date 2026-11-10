/**
 * The shapes the API sends and receives (see docs/api.md). Dates are "YYYY-MM-DD" and times "HH:mm"
 * in studio time (Europe/Sofia); `…At` fields are ISO UTC instants; money is EUR.
 */

/** An uploaded picture: `url` up to 2000 px, `thumbUrl` up to 600 px wide. */
export type Image = { id: string; url: string; thumbUrl: string }

export type Weekday = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY"
export type WorkingHours = { day: Weekday; start: string; end: string }
export type DayException = {
  date: string
  closed: boolean
  start: string | null
  end: string | null
  note: string | null
}

export type Artist = {
  id: string
  name: string
  slug: string
  style: string
  bio: string
  hourlyRate: number | null
  dayRate: number | null
  signaturePiece: Image | null
  weeklyHours: WorkingHours[]
  exceptions: DayException[]
  averageRating: number | null
  reviewCount: number
}

export type FlashSize = "SMALL" | "MEDIUM" | "LARGE"

export type Flash = {
  id: string
  artistId: string
  artistName: string
  name: string
  widthCm: number
  heightCm: number
  size: FlashSize
  durationMinutes: number
  price: number
  deposit: number
  image: Image | null
  /** False once booked (every design is tattooed once) or taken off the sheet. */
  bookable: boolean
}

export type Work = { id: string; artistId: string; image: Image; caption: string; createdAt: string }

export type Review = { id: string; artistId: string; name: string; rating: number; text: string; createdAt: string }
export type ReviewSummary = { averageRating: number; count: number; reviews: Review[] }

export type Availability = {
  artistId: string
  flashId: string | null
  durationMinutes: number
  days: { date: string; closed: boolean; slots: { time: string; startsAt: string }[] }[]
}

export type BookingType = "FLASH" | "CONSULTATION" | "SESSION"
export type BookingStatus = "PENDING" | "BOOKED" | "CANCELLED" | "EXPIRED" | "DONE" | "NO_SHOW"
export type DepositStatus = "NONE" | "WAITING" | "PAID" | "REFUNDED" | "KEPT" | "NOT_PAID"
export type Placement =
  | "FOREARM"
  | "UPPER_ARM"
  | "FULL_SLEEVE"
  | "SHOULDER"
  | "CHEST"
  | "BACK"
  | "RIBS"
  | "THIGH"
  | "CALF"
  | "OTHER"
export type ColourChoice = "FULL_COLOUR" | "BLACK_AND_GREY" | "NOT_SURE"

export type Deposit = {
  amount: number | null
  status: DepositStatus
  method: "ONLINE" | "CASH" | null
  dueAt: string | null
  paidAt: string | null
  refundedAt: string | null
}

export type Consultation = {
  idea: string | null
  placement: Placement | null
  sizeCm: number | null
  colour: ColourChoice | null
  photos: Image[]
}

export type Booking = {
  code: string
  type: BookingType
  status: BookingStatus
  /** The design's name or the session's description; null for consultations. */
  title: string | null
  flashId: string | null
  widthCm: number | null
  heightCm: number | null
  artistId: string
  artistName: string | null
  date: string
  time: string
  endTime: string
  startsAt: string
  endsAt: string
  price: number | null
  deposit: Deposit
  /** Paid in the studio: the price minus the deposit. */
  leftToPay: number | null
  customerName: string | null
  phoneMasked: string | null
  consultation: Consultation | null
  cancellable: boolean
  refundDeadline: string | null
  refundIfCancelledNow: boolean
  canPayDeposit: boolean
  canReview: boolean
}

export type PaymentStart = { checkoutUrl: string; amount: number; dueAt: string | null }

export type CreateFlashBooking = {
  flashId: string
  date: string
  time: string
  customerName: string
  phone: string
  email: string
  dateOfBirth: string
  notes: string | null
  adult: boolean
  consentForm: boolean
  depositPolicy: boolean
}

export type CreateConsultation = {
  artistId: string
  date: string
  time: string
  customerName: string
  phone: string
  email: string
  dateOfBirth: string
  idea: string
  placement: Placement
  sizeCm: number | null
  colour: ColourChoice
  adult: boolean
  consentForm: boolean
}

/** Machine-readable error codes from the API (see server ErrorCode.java). */
export type ErrorCode =
  | "VALIDATION_FAILED"
  | "ARTIST_NOT_FOUND"
  | "FLASH_NOT_FOUND"
  | "FLASH_NOT_AVAILABLE"
  | "OUTSIDE_BOOKING_WINDOW"
  | "INVALID_PHONE"
  | "UNDER_AGE"
  | "PHONE_LIMIT_REACHED"
  | "SLOT_UNAVAILABLE"
  | "BOOKING_NOT_FOUND"
  | "BOOKING_NOT_ACTIVE"
  | "CANCELLATION_CLOSED"
  | "HOLD_EXPIRED"
  | "NOTHING_TO_PAY"
  | "REVIEW_NOT_ALLOWED"
  | "INVALID_FILE"
  | "TOO_MANY_ATTEMPTS"
  | "TOO_MANY_SUBMISSIONS"
  | "INVALID_LOGIN"
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"

export type ApiErrorBody = {
  status: number
  title: string
  detail: string
  code?: ErrorCode
  errors?: Record<string, string>
}

export type AuthUser = {
  username: string
  /** USER: a customer with an account */
  role: "ADMIN" | "ARTIST" | "USER"
  artistId: string | null
  name: string | null
}

export type LoginResponse = { token: string; expiresAt: string; user: AuthUser }

export type Account = { name: string; email: string; phone: string; dateOfBirth: string | null; createdAt: string }

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

// ---- The artist and admin area (/staff/**, /admin/**) ----

export type Role = "USER" | "ARTIST" | "ADMIN"

/** A booking as staff see it: the customer's full details, no customer-only fields. */
export type StaffBooking = Omit<
  Booking,
  "phoneMasked" | "cancellable" | "refundDeadline" | "refundIfCancelledNow" | "canPayDeposit" | "canReview" | "leftToPay"
> & {
  id: string
  phone: string | null
  email: string | null
  dateOfBirth: string | null
  notes: string | null
  /** A deposit still waiting after it was due. */
  depositOverdue: boolean
  followsBookingId: string | null
  anonymized: boolean
  /** BOOKED but already over: mark it Done or No-show. */
  awaitingStatus: boolean
  createdAt: string
}

/** Someone from the artist's consultations and sessions, to reserve a session for. */
export type CustomerOption = {
  bookingId: string
  code: string
  name: string
  phone: string
  email: string | null
  lastVisit: string
  about: string | null
}

export type Schedule = {
  weeklyHours: WorkingHours[]
  exceptions: DayException[]
  breakMinutes: number
  /** Upcoming customer bookings outside the hours. */
  conflicts: StaffBooking[]
}

export type FlashState = "ON_SHEET" | "HIDDEN" | "HELD" | "BOOKED" | "TATTOOED"

export type AdminFlash = {
  id: string
  artistId: string
  artistName: string
  name: string
  widthCm: number
  heightCm: number
  size: FlashSize
  durationMinutes: number
  price: number
  image: Image | null
  available: boolean
  state: FlashState
  bookingCode: string | null
  sortOrder: number
  createdAt: string
}

export type FlashInput = {
  artistId?: string
  name: string
  widthCm: number
  heightCm: number
  durationMinutes: number
  price: number
  available: boolean
}

export type AdminArtist = {
  id: string
  name: string
  slug: string
  style: string
  bio: string
  hourlyRate: number | null
  dayRate: number | null
  signaturePiece: Image | null
  weeklyHours: WorkingHours[]
  exceptions: DayException[]
  breakMinutes: number
  sortOrder: number
  active: boolean
  username: string | null
  conflicts: StaffBooking[]
}

export type ArtistOption = { id: string; name: string; active: boolean; username: string | null }

export type AdminReview = Review & { bookingId: string | null; approved: boolean }

export type AdminUser = {
  id: string
  username: string
  role: Role
  active: boolean
  name: string | null
  email: string | null
  phone: string | null
  artistId: string | null
  artistName: string | null
  createdAt: string | null
  lastLoginAt: string | null
  loginCount: number
}

export type Message = {
  id: string
  name: string
  contact: string
  text: string
  photos: Image[]
  read: boolean
  readAt: string | null
  createdAt: string
  /** 1st, 2nd, … message from this email or phone. */
  senderMessageNumber: number
}

export type Stats = {
  from: string
  to: string
  bookings: number
  done: number
  noShows: number
  cancelled: number
  revenue: number
  busiestArtist: { id: string; name: string; bookings: number } | null
  perArtist: { artistId: string; name: string; bookings: number; done: number; noShows: number; revenue: number }[]
}
