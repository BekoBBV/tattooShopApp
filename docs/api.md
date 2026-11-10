# Blade & Brush API

The REST API the React app talks to (the data behind it: [database.md](database.md)); `client/src/lib/api.ts` is the only place that calls it. Base path `/api`. While the server runs, the same endpoints can be tried in Swagger UI at http://localhost:8080/swagger-ui.html.

- Dates are `YYYY-MM-DD` and times `HH:mm`, both in studio time (Europe/Sofia). `startsAt`, `endsAt`, `createdAt`, `dueAt` and the other `…At` fields are ISO UTC instants.
- Prices are numbers in EUR.
- Date ranges (`from` / `to`) include both days.
- Everything runs on a 30-minute grid: bookings start (and sessions end) on the hour or half hour.

## How booking works

| What | How it's booked | Deposit | Status while it waits |
|---|---|---|---|
| **Flash design** | The customer picks a design and a time | €30, paid online within 15 minutes, taken off the price | `PENDING` (time and design held), then `BOOKED` once paid, or `EXPIRED` if nobody paid |
| **Consultation** | The customer picks an artist and a time (30 minutes), with their idea and up to 5 reference photos | none | `BOOKED` at once |
| **Session** | The artist reserves it for a customer from one of their consultations | set by the artist (may be 0), due within 72 hours | `BOOKED` at once; the deposit shows as waiting |

- **One of a kind:** a flash design can be claimed by one booking only. While it is held, booked or tattooed it is off the sheet; a cancelled, expired or missed booking puts it back.
- **Breaks:** each artist has a break after every client (30 minutes by default). It counts as taken time.
- **Age:** customers must be 18 or older on the day of the appointment.
- **Cancelling:** customers can cancel online until the appointment starts. A paid deposit is refunded when that is at least 48 hours ahead, otherwise the studio keeps it. When the studio cancels, the deposit is always refunded.
- **Payments:** deposits are paid on a payment page (`checkoutUrl`). Until the studio's Revolut account is connected, that page is the server's own test page, which confirms the payment with a button and moves no money.

## Lists: paging, sorting, search

Every list below marked *page* takes the same query parameters and answers with one page:

| Parameter | Meaning |
|---|---|
| `page` | Page number, starting at 0 (default 0) |
| `size` | Items per page, 1–500 (default 10) |
| `sort` | `field,asc` or `field,desc`; the allowed fields are listed with each endpoint |
| `q` | Search text (not case-sensitive, taken literally) |

```ts
type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number }
```

An unknown sort field, a page below 0 or a size above 500 is `400 VALIDATION_FAILED`. Guests get the plain public lists; searching, sorting and paging them (`/artists/search`, `/reviews/search`) needs any signed-in token.

## Uploads

Pictures are sent as `multipart/form-data`. Requests that carry both data and pictures send the data as a JSON part (with `Content-Type: application/json`, e.g. `new Blob([JSON.stringify(body)], { type: "application/json" })`) next to the picture parts. Pictures must be real JPG, PNG, HEIC or WebP files (the server reads their first bytes), at most 10 MB each and at most 5 per request; otherwise `INVALID_FILE`.

```ts
type Image = { id: string; url: string; thumbUrl: string }  // thumbUrl: up to 600 px wide
```

With Cloudinary the addresses point there (and HEIC photos are delivered as JPG or WebP); without it they are served by this server at `/uploads/…`.

## Errors

Every error is a JSON problem detail with a stable `code` the frontend can react to. Show `detail` to the user. `errors` (field → message) only comes with `VALIDATION_FAILED` on invalid request bodies.

```json
{ "status": 409, "title": "Conflict", "detail": "Sorry, that time was just taken. Please pick another time.", "code": "SLOT_UNAVAILABLE" }
```

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Invalid input (see `errors`), e.g. a missing confirmation, a bad date range, hours or times off the grid |
| `ARTIST_NOT_FOUND` / `FLASH_NOT_FOUND` / `BOOKING_NOT_FOUND` / `NOT_FOUND` | 404 | Unknown, hidden or (for artists) someone else's item |
| `FLASH_NOT_AVAILABLE` | 409 | The design was just booked by someone else, or taken off the sheet |
| `OUTSIDE_BOOKING_WINDOW` | 400 | Less than 2 hours or more than 30 days ahead (sessions: up to a year) |
| `INVALID_PHONE` | 400 | The phone number can't be read |
| `UNDER_AGE` | 400 | Younger than 18 on the day of the appointment (or when signing up) |
| `PHONE_LIMIT_REACHED` | 409 | The phone number (or the signed-in customer account) already has 2 upcoming flash bookings or consultations |
| `SLOT_UNAVAILABLE` | 409 | The time (or the break after it) is taken, or outside the artist's hours |
| `BOOKING_NOT_ACTIVE` | 409 | The booking is no longer held or booked (cancelled, expired, done, …) |
| `CANCELLATION_CLOSED` | 409 | The appointment has already started |
| `HOLD_EXPIRED` | 409 | The 15 minutes to pay the deposit ran out and the time was released: pick a time again |
| `NOTHING_TO_PAY` | 409 | No deposit is waiting (already paid, or none needed) |
| `REVIEW_NOT_ALLOWED` | 409 | Only finished flash and session appointments can be reviewed, once each |
| `INVALID_FILE` | 400 / 413 | Not a JPG, PNG, HEIC or WebP picture, over 10 MB, or more than 5 |
| `CONFLICT` | 409 | Clashes with current data, e.g. deactivating an artist with upcoming bookings, a username or page address that is taken, deleting a booked design, or a booking someone else just changed |
| `TOO_MANY_ATTEMPTS` | 429 | 5 wrong code/phone lookups from one visitor in 15 minutes; 10 failed sign-ins from one visitor in 15 minutes; or 5 wrong passwords for one username in 5 minutes |
| `TOO_MANY_SUBMISSIONS` | 429 | More than 3 reviews, 5 messages, 10 bookings or 5 new accounts per visitor per hour |
| `INVALID_LOGIN` | 401 | Wrong username / email or password |
| `UNAUTHORIZED` | 401 | Missing, expired or invalid token |
| `FORBIDDEN` | 403 | Signed in, but not allowed (e.g. an artist on an admin page, or a deactivated artist) |

The same list lives in `server/.../error/ErrorCode.java` and `client/src/lib/types.ts`. A test fails if they differ.

## The studio (public)

| Method & path | Query | Response |
|---|---|---|
| `GET /artists` | | `Artist[]` (active ones, in studio order) |
| `GET /artists/search` *(signed in)* | *page* + `day` (works that weekday, e.g. `SATURDAY`); `q` = name, style, bio; sort `order`, `name` | `Page<Artist>` |
| `GET /artists/{slugOrId}` | | `Artist`, by page name (`kai`) or id |
| `GET /artists/{slugOrId}/works` | | `Work[]`, newest first |
| `GET /flash` | `artistId`, `size` (`SMALL` up to 8 cm on the longer side, `MEDIUM` up to 12 cm, `LARGE`) | `Flash[]`: the sheet, only designs that can be booked now |
| `GET /flash/{id}` | | `Flash` (`bookable: false` once booked or off the sheet) |
| `GET /reviews` | `artistId` (optional) | `{ averageRating, count, reviews: Review[] }`, approved only |
| `GET /reviews/search` *(signed in)* | *page* + `minRating` (1–5), `artistId`; `q` = name, text; sort `date`, `rating`, `name` | `Page<Review>` (approved only) |
| `POST /messages` | JSON `{ name, contact, text }`, or multipart: part `message` (that JSON) + up to 5 `photos` | `201` |
| `GET /health` | | `{ status, database }` |

```ts
type WorkingHours = { day: "MONDAY" | … | "SUNDAY"; start: string; end: string }
type DayException = { date: string; closed: boolean; start: string | null; end: string | null; note: string | null }
type Artist = {
  id: string; name: string; slug: string; style: string; bio: string
  hourlyRate: number | null; dayRate: number | null
  signaturePiece: Image | null      // the picture on the hanging scroll
  weeklyHours: WorkingHours[]       // Monday first
  exceptions: DayException[]        // upcoming days off / special hours
  averageRating: number | null      // one decimal, null without reviews
  reviewCount: number
}
type Flash = {
  id: string; artistId: string; artistName: string; name: string
  widthCm: number; heightCm: number; size: "SMALL" | "MEDIUM" | "LARGE"
  durationMinutes: number; price: number; deposit: number; image: Image | null; bookable: boolean
}
type Work = { id: string; artistId: string; image: Image; caption: string; createdAt: string }
type Review = { id: string; artistId: string; name: string; rating: number; text: string; createdAt: string }
```

## Booking (public)

| Method & path | Body / query | Response |
|---|---|---|
| `GET /availability` | `flashId` (its artist and time) **or** `artistId` (a 30-minute consultation), `from` (default today), `days` (1–31, default 14) | `{ artistId, flashId \| null, durationMinutes, days: [{ date, closed, slots: [{ time, startsAt }] }] }`; `closed`: the artist doesn't work that day |
| `POST /bookings/flash` | `{ flashId, date, time, customerName, phone, email, dateOfBirth, notes \| null, adult: true, consentForm: true, depositPolicy: true }` | `201 Booking` with status `PENDING` and `deposit.dueAt` (15 minutes): pay with `POST /bookings/deposit` |
| `POST /bookings/consultation` | multipart: part `booking` = `{ artistId, date, time, customerName, phone, email, dateOfBirth, idea, placement, sizeCm \| null, colour, adult: true, consentForm: true }` + up to 5 `photos` | `201 Booking` (BOOKED, an email is sent) |
| `POST /bookings/deposit` | `{ code, phone }` | `{ checkoutUrl, amount, dueAt }`: send the customer to `checkoutUrl`. They come back to `/booking/confirmed?code=…`, or to `/manage?refunded=1&code=…` if the hold had run out, someone else took the time and the money was refunded, or to `/book/deposit?code=…` if they cancelled the payment |
| `POST /bookings/lookup` | `{ code, phone }` | `Booking` |
| `POST /bookings/cancel` | `{ code, phone }` | `Booking` (see the cancelling rule above) |
| `POST /bookings/review` | `{ code, phone, rating (1–5), text }` | `201` (shown after the admin approves it) |

Signed in as a customer (token) when booking? The booking also goes to My account. A token that no longer works is ignored there: it becomes a guest booking. `429 TOO_MANY_SUBMISSIONS` after 10 bookings from one visitor in an hour (only bookings that went through count). Emails go out when a booking is confirmed (flash: once the deposit is paid), when an artist reserves a session, a day before, and when a booking is cancelled.

`placement` is one of `FOREARM, UPPER_ARM, FULL_SLEEVE, SHOULDER, CHEST, BACK, RIBS, THIGH, CALF, OTHER`; `colour` one of `FULL_COLOUR, BLACK_AND_GREY, NOT_SURE`.

```ts
type BookingType = "FLASH" | "CONSULTATION" | "SESSION"
type BookingStatus = "PENDING" | "BOOKED" | "CANCELLED" | "EXPIRED" | "DONE" | "NO_SHOW"
type DepositStatus = "NONE" | "WAITING" | "PAID" | "REFUNDED" | "KEPT" | "NOT_PAID"
type Deposit = {
  amount: number | null; status: DepositStatus; method: "ONLINE" | "CASH" | null
  dueAt: string | null; paidAt: string | null; refundedAt: string | null
}
type Consultation = { idea: string | null; placement: string | null; sizeCm: number | null; colour: string | null; photos: Image[] }
type Booking = {
  code: string; type: BookingType; status: BookingStatus
  title: string | null                 // design name or session description; null for consultations
  flashId: string | null; widthCm: number | null; heightCm: number | null
  artistId: string; artistName: string
  date: string; time: string; endTime: string; startsAt: string; endsAt: string
  price: number | null; deposit: Deposit; leftToPay: number | null   // paid in the studio
  customerName: string | null; phoneMasked: string | null            // null once wiped (180 days after)
  consultation: Consultation | null
  cancellable: boolean
  refundDeadline: string | null        // cancel by then to get a paid deposit back
  refundIfCancelledNow: boolean
  canPayDeposit: boolean
  canReview: boolean
}
```

Status labels on the site: `PENDING` "Waiting for deposit", `BOOKED` "Deposit paid" / "Consultation booked" / "Waiting for deposit" (a session with `deposit.status` WAITING), `CANCELLED` "Cancelled" (plus "Deposit refunded" when `deposit.status` is REFUNDED), `EXPIRED` "Hold expired".

## Sign in (staff and customers)

| Method & path | Body | Response |
|---|---|---|
| `POST /auth/login` | `{ username, password }` (customers: their email as `username`) | `{ token, expiresAt, user: { username, role: "ADMIN" \| "ARTIST" \| "USER", artistId \| null, name \| null } }` |
| `POST /auth/register` | `{ name, phone, email, dateOfBirth, password (8+) }` | `201`, same as sign in (the new customer is signed in). `409 CONFLICT` if the email already has an account, `UNDER_AGE` under 18 |
| `GET /auth/me` | | `{ username, role, artistId, name }` |

Send the token as `Authorization: Bearer <token>` on every `/staff/**`, `/admin/**` and `/account/**` call. It is valid for 12 hours. Usernames and emails are not case-sensitive. One visitor can create 5 accounts per hour (`429 TOO_MANY_SUBMISSIONS`). A token stops working as soon as its login is deleted or blocked, its password is reset, or its role or artist changes; a new account that reuses a deleted account's email never inherits the old tokens. One visitor gets 10 failed sign-ins per 15 minutes, and a username is blocked for 5 minutes after 5 wrong passwords.

## Customer account (`/account/**`, role USER)

| Method & path | Body / query | Response |
|---|---|---|
| `GET /account` | | `{ name, email, phone, dateOfBirth, createdAt }` |
| `PUT /account` | `{ name, phone, email }` (the date of birth can't be changed) | A new sign-in (same as sign in), with the new name and email. The email is the sign-in, so a new one signs other devices out and the old email no longer signs in. `409 CONFLICT` if another account has the email, `INVALID_PHONE` |
| `GET /account/bookings` | *page* + `when` (`UPCOMING` default, `PAST`, `ALL`), `type` (e.g. `CONSULTATION` for the Consultations tab), `status`; `q` = title, idea, code; sort `date`, `title`, `type`, `price`, `status` | `Page<Booking>`: bookings made while signed in and sessions reserved for them (upcoming soonest first, otherwise latest first) |
| `POST /account/bookings/{code}/cancel` | | `Booking` (same rules as `/bookings/cancel`; `404 BOOKING_NOT_FOUND` for someone else's booking) |
| `POST /account/bookings/{code}/deposit` | | `{ checkoutUrl, amount, dueAt }` |
| `POST /account/bookings/{code}/review` | `{ rating, text }` | `201` |
| `PUT /account/password` | `{ currentPassword, newPassword (8+) }` | A new sign-in (same as sign in); other devices are signed out. `400` if the current password is wrong |
| `POST /account/delete` | `{ password }` | `204`: account deleted; bookings stay for the studio without the account |

## Artist (`/staff/**`, the signed-in artist only)

| Method & path | Body / query | Response |
|---|---|---|
| `GET /staff/bookings` | *page* + `from`, `to`, `status`, `type` (default: last 7 days to 30 days ahead); `q` = customer, phone (as typed), email, code, title, idea, notes; sort `date`, `title`, `type`, `customer`, `price`, `status` | `Page<StaffBooking>`, earliest first: the calendar |
| `PATCH /staff/bookings/{id}` | `{ status: "DONE" \| "NO_SHOW" \| "CANCELLED" }` | `StaffBooking` |
| `GET /staff/customers` | `q` = name, phone, email | `CustomerOption[]`: people from this artist's consultations and sessions, latest booking each |
| `POST /staff/sessions` | `{ customerBookingId, date, start, end, description, price, deposit }` | `201 StaffBooking`: the time is taken from online booking (break included) and the customer gets an email to pay the deposit within 72 hours |
| `GET /staff/schedule` | | `{ weeklyHours, exceptions, breakMinutes, conflicts: StaffBooking[] }` (upcoming exceptions only) |
| `PUT /staff/schedule` | `{ weeklyHours, exceptions, breakMinutes }` (`breakMinutes` 0–120 in steps of 30, optional) | same as GET (sorted; past dates dropped). Start times on the hour or half hour |
| `GET /staff/flash` | *page* + `available`; `q` = name; sort `order`, `name`, `price`, `time`, `created` | `Page<AdminFlash>`: own designs with their state |
| `POST /staff/flash` | multipart: part `flash` = `{ name, widthCm, heightCm, durationMinutes (multiple of 30), price, available }` + optional `image` | `201 AdminFlash` |
| `PUT /staff/flash/{id}` | `{ name, widthCm, heightCm, durationMinutes, price, available }` | `AdminFlash` (bookings keep the name, size and price they were booked with) |
| `PATCH /staff/flash/{id}` | `{ active }` (the Available switch) | `AdminFlash` |
| `PUT /staff/flash/{id}/image` | multipart `image` | `AdminFlash` |
| `DELETE /staff/flash/{id}` | | `204`; `409` while it is held or booked |
| `GET /staff/works` | | `Work[]` |
| `POST /staff/works` | multipart `image` + optional `caption` (max 60) | `201 Work` (up to 60 photos) |
| `DELETE /staff/works/{id}` | | `204` |
| `PUT /staff/signature` | multipart `image` | `Image`: the signature piece on the scroll |

`conflicts` lists upcoming customer bookings (flash, consultations) that fall outside the hours, e.g. after adding a day off. The hours are saved anyway; the bookings stay until someone cancels them. Sessions an artist reserved may lie outside the hours on purpose and don't count.

Status rules (also for the admin): *Done* / *No-show* only once the appointment has started, and switching between the two corrects a mistake. *Cancel* works on held or booked appointments at any time, refunds a paid deposit and emails the customer if the appointment is still ahead. A cancelled booking stays cancelled.

```ts
type StaffBooking = Omit<Booking, "phoneMasked" | "cancellable" | "refundDeadline" | "refundIfCancelledNow" | "canPayDeposit" | "canReview" | "leftToPay"> & {
  id: string
  phone: string | null; email: string | null; dateOfBirth: string | null; notes: string | null
  depositOverdue: boolean       // a deposit still waiting after it was due (highlighted)
  followsBookingId: string | null
  anonymized: boolean
  awaitingStatus: boolean       // BOOKED but already over: mark it Done or No-show
  createdAt: string
}
type CustomerOption = { bookingId: string; code: string; name: string; phone: string; email: string | null; lastVisit: string; about: string | null }
type AdminFlash = Omit<Flash, "deposit" | "bookable"> & {
  available: boolean
  state: "ON_SHEET" | "HIDDEN" | "HELD" | "BOOKED" | "TATTOOED"
  bookingCode: string | null; sortOrder: number; createdAt: string
}
```

## Admin (`/admin/**`)

| Method & path | Body / query | Response |
|---|---|---|
| `GET /admin/bookings` | *page* + `artistId`, `from`, `to`, `status`, `type`, `depositStatus`, `depositMethod` (`ONLINE` = paid with Revolut, `CASH`); `q` and sort as for `/staff/bookings` | `Page<StaffBooking>` |
| `PATCH /admin/bookings/{id}` | `{ status }` | `StaffBooking` |
| `POST /admin/bookings/{id}/deposit-cash` | | `StaffBooking`: the waiting deposit is paid in cash (a held flash booking is confirmed) |
| `PUT /admin/bookings/{id}` | `{ customerName, phone, email \| null, notes \| null }` | `StaffBooking` (`409` once the details were wiped for privacy) |
| `DELETE /admin/bookings/{id}` | | `204`: gone for good with its photos, no email (cancel instead to tell the customer) |
| `GET /admin/stats` | `?from&to` (default: this week, Monday to Sunday) | `Stats` |
| `GET /admin/flash` | *page* + `artistId`, `available`; `q` = name; sort as for `/staff/flash` | `Page<AdminFlash>`: every artist's designs |
| `POST /admin/flash` | multipart: part `flash` (as for staff, plus `artistId`) + optional `image` | `201 AdminFlash` |
| `PUT /admin/flash/{id}`, `PATCH /admin/flash/{id}`, `PUT /admin/flash/{id}/image`, `DELETE /admin/flash/{id}` | as for `/staff/flash` | |
| `GET /admin/artists` | *page* + `active`, `day`; `q` = name, style, bio; sort `order`, `name` | `Page<AdminArtist>` (including inactive ones) |
| `POST /admin/artists` | `{ name, slug \| null (made from the name; kept on edits when left out), style, bio, hourlyRate, dayRate, breakMinutes, weeklyHours, exceptions }` | `201 AdminArtist`; `409` if the page address is taken |
| `GET /admin/artists/options` | | `ArtistOption[]`: every artist (inactive too) by name, `{ id, name, active, username }`, for pickers |
| `PUT /admin/artists/{id}` | same, plus optional `active` (`false` deactivates them, blocks their login and takes their designs off the sheet; `409` while they have upcoming bookings) | `AdminArtist` |
| `PATCH /admin/artists/{id}` | `{ active }`: (de)activates them, hours stay as they are | `AdminArtist` |
| `PUT /admin/artists/{id}/signature` / `DELETE …` | multipart `image` | `Image` / `204` |
| `DELETE /admin/artists/{id}` | | `204`: deletes the artist, their login, designs and portfolio. Only for artists without any bookings (`409` otherwise: deactivate them instead) |
| `POST /admin/artists/{id}/login` | `{ username (3–30 of a-z 0-9 . _ -), password (8+) }` | `201 { username }`: creates or resets their one login (signs out the old sessions; a login blocked under *Users* stays blocked) |
| `GET /admin/reviews` | *page* + `status=pending\|approved` (all if left out), `minRating`, `artistId`; `q` = name, text; sort `date`, `rating`, `name` | `Page<AdminReview>`, newest first |
| `PATCH /admin/reviews/{id}` | `{ approved }` ("Show on site") | `AdminReview` |
| `PUT /admin/reviews/{id}` | `{ name, rating, text, approved }` | `AdminReview` |
| `DELETE /admin/reviews/{id}` | | `204` |
| `GET /admin/messages` | *page* + `read` (true = handled); `q` = name, contact, text; sort `date`, `name` | `Page<Message>`, newest first (deleted automatically after 90 days, photos included) |
| `PATCH /admin/messages/{id}` | `{ read }` ("Mark as handled") | `Message` |
| `DELETE /admin/messages/{id}` | | `204` |
| `GET /admin/users` | *page* + `role` (`USER`, `ARTIST`, `ADMIN`), `active`; `q` = username, name, email, phone; sort `username`, `name`, `role`, `created`, `lastLogin` | `Page<AdminUser>` |
| `POST /admin/users` | `{ role, password (8+), username (staff), artistId (artists), name, email, phone (customers) }` | `201 AdminUser` |
| `PUT /admin/users/{id}` | `{ role, active, artistId, name, phone }` | `AdminUser`. A change of role or artist signs them out. Refused (`409`) for your own login and for the last active admin |
| `PUT /admin/users/{id}/password` | `{ password (8+) }` | `204`, signs them out everywhere |
| `DELETE /admin/users/{id}` | | `204` (not yourself, not the last admin). A customer's bookings stay, without the account |

```ts
type AdminArtist = Omit<Artist, "averageRating" | "reviewCount"> & {
  breakMinutes: number; sortOrder: number; active: boolean; username: string | null
  conflicts: StaffBooking[]  // upcoming customer bookings outside their hours
}
type ArtistOption = { id: string; name: string; active: boolean; username: string | null }
type AdminReview = Review & { bookingId: string | null; approved: boolean }
type AdminUser = {
  id: string; username: string; role: "USER" | "ARTIST" | "ADMIN"; active: boolean
  name: string | null; email: string | null; phone: string | null   // customers
  artistId: string | null; artistName: string | null                 // artists
  createdAt: string | null; lastLoginAt: string | null; loginCount: number
}
type Message = {
  id: string; name: string; contact: string; text: string; photos: Image[]
  read: boolean; readAt: string | null; createdAt: string
  senderMessageNumber: number   // 1st, 2nd, … message from this email / phone (any phone format, any email case)
}
type Stats = {
  from: string; to: string
  bookings: number          // confirmed: booked, done or missed
  done: number; noShows: number; cancelled: number
  revenue: number           // DONE bookings at their price, plus deposits kept after late cancellations
  busiestArtist: { id: string; name: string; bookings: number } | null
  perArtist: { artistId: string; name: string; bookings: number; done: number; noShows: number; revenue: number }[]
}
```
