export type Accent = "red" | "orange" | "yellow" | "green" | "cyan" | "purple"
export type Day = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY"

export type Service = {
  id: string
  name: string
  description: string
  price: number
  label: string
  palette: string
}
export type WorkingHours = { day: Day; start: string; end: string }
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
  handle: string
  specialty: string
  bio: string
  photoUrl: string | null
  accent: Accent
  weeklyHours: WorkingHours[]
  /** Upcoming one-off days off or custom hours */
  exceptions: DayException[]
}
export type Review = {
  id: string
  name: string
  rating: number
  text: string
  createdAt: string
}
export type BookingStatus = "BOOKED" | "CANCELLED" | "DONE" | "NO_SHOW"
export type Booking = {
  code: string
  status: BookingStatus
  serviceName: string
  artistId: string
  artistName: string
  date: string
  time: string
  startsAt: string
  endsAt: string
  price: number
  customerName: string | null
  phoneMasked: string | null
  notes: string | null
  cancellable: boolean
  cancelDeadline: string
}
export type StaffBooking = {
  id: string
  code: string
  status: BookingStatus
  serviceName: string
  artistId: string
  artistName: string
  date: string
  time: string
  startsAt: string
  endsAt: string
  price: number
  customerName: string | null
  phone: string | null
  email: string | null
  notes: string | null
  anonymized: boolean
  awaitingStatus: boolean
}
export type Message = {
  id: string
  name: string
  contact: string
  text: string
  read: boolean
  createdAt: string
  /** 1 = first message from this email / phone, 2 = second, … */
  senderMessageNumber: number
  /** When it was marked read (null while unread) */
  readAt: string | null
}
export type Availability = {
  serviceId: string
  artistId: string | null
  days: {
    date: string
    slots: { time: string; startsAt: string; freeArtists: number }[]
  }[]
}
/** Machine-readable error codes from the API (see server ErrorCode.java). */
export type ErrorCode =
  | "VALIDATION_FAILED"
  | "SERVICE_NOT_FOUND"
  | "ARTIST_NOT_FOUND"
  | "OUTSIDE_BOOKING_WINDOW"
  | "INVALID_PHONE"
  | "PHONE_LIMIT_REACHED"
  | "SLOT_UNAVAILABLE"
  | "BOOKING_NOT_FOUND"
  | "BOOKING_NOT_ACTIVE"
  | "CANCELLATION_CLOSED"
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
  /** Customers only */
  name: string | null
}
/** A customer's own account details */
export type Account = { name: string; email: string; phone: string; createdAt: string }
export type LoginResponse = { token: string; expiresAt: string; user: AuthUser }
export type AdminStats = {
  from: string
  to: string
  bookings: number
  done: number
  noShows: number
  cancelled: number
  revenue: number
  busiestArtist: { id: string; name: string; bookings: number } | null
  perArtist: {
    artistId: string
    name: string
    bookings: number
    done: number
    noShows: number
    revenue: number
  }[]
}
export type AdminArtist = Artist & {
  sortOrder: number
  active: boolean
  /** Login username, null if the artist has no login yet */
  username: string | null
  /** Upcoming bookings outside their hours (they stay booked until someone cancels them) */
  conflicts: StaffBooking[]
}
/** An artist in the admin's pickers (/admin/artists/options): no hours or bookings */
export type ArtistOption = {
  id: string
  name: string
  active: boolean
  /** Login username, null if the artist has no login yet */
  username: string | null
}
export type AdminService = Service & { sortOrder: number; active: boolean }
export type AdminReview = Review & { approved: boolean }
export type Role = AuthUser["role"]
/** A login on the admin's users & rights page */
export type AdminUser = {
  id: string
  username: string
  role: Role
  active: boolean
  /** Customers only */
  name: string | null
  email: string | null
  phone: string | null
  /** Artists only */
  artistId: string | null
  artistName: string | null
  createdAt: string | null
  lastLoginAt: string | null
  loginCount: number
}
