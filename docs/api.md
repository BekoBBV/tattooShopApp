# Tattoo Studio API

The REST API the React app talks to (the data behind it: [database.md](database.md)) (`client/src/lib/api.ts` is the only place that calls it). Base path `/api`. While the server runs, the same endpoints can be tried in Swagger UI at http://localhost:8080/swagger-ui.html.

- Dates are `YYYY-MM-DD` and times `HH:mm`, both in shop time (Europe/Sofia). `startsAt`, `endsAt`, `createdAt` and `expiresAt` are ISO UTC instants.
- Prices are numbers in EUR.
- Date ranges (`from` / `to`) include both days.

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

An unknown sort field, a page below 0 or a size above 500 is `400 VALIDATION_FAILED`. Guests get the plain public lists; searching, sorting and paging them (`/services/search`, `/artists/search`, `/reviews/search`) needs any signed-in token.

## Errors

Every error is a JSON problem detail with a stable `code` the frontend can react to. Show `detail` to the user. `errors` (field → message) only comes with `VALIDATION_FAILED` on invalid request bodies.

```json
{ "status": 409, "title": "Conflict", "detail": "Sorry, that time was just taken. Please pick another time.", "code": "SLOT_UNAVAILABLE" }
```

| Code | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Invalid input (see `errors`), e.g. a bad date range or opening hours |
| `SERVICE_NOT_FOUND` / `ARTIST_NOT_FOUND` / `BOOKING_NOT_FOUND` / `NOT_FOUND` | 404 | Unknown, hidden or (for artists) someone else's item |
| `OUTSIDE_BOOKING_WINDOW` | 400 | Less than 1 hour or more than 30 days ahead |
| `INVALID_PHONE` | 400 | The phone number can't be read |
| `PHONE_LIMIT_REACHED` | 409 | The phone number (or the signed-in customer account) already has 2 upcoming bookings |
| `SLOT_UNAVAILABLE` | 409 | The time is taken or outside the artist's hours |
| `BOOKING_NOT_ACTIVE` | 409 | The booking is no longer booked (cancelled, done, …) |
| `CANCELLATION_CLOSED` | 409 | Less than 2 hours before the appointment |
| `CONFLICT` | 409 | Clashes with current data, e.g. deactivating an artist with upcoming bookings, a username or email that is taken, or a booking someone else just changed |
| `TOO_MANY_ATTEMPTS` | 429 | 5 wrong code/phone lookups from one visitor in 15 minutes; 10 failed sign-ins from one visitor in 15 minutes; or 5 wrong passwords for one username in 5 minutes |
| `TOO_MANY_SUBMISSIONS` | 429 | More than 3 reviews, 5 messages, 10 bookings or 5 new accounts per visitor per hour |
| `INVALID_LOGIN` | 401 | Wrong username / email or password |
| `UNAUTHORIZED` | 401 | Missing, expired or invalid token |
| `FORBIDDEN` | 403 | Signed in, but not allowed (e.g. an artist on an admin page, or a deactivated artist) |

The same list lives in `server/.../error/ErrorCode.java` and `client/src/lib/types.ts`. A test fails if they differ.

## Public

| Method & path | Body / query | Response |
|---|---|---|
| `GET /services` | | `Service[]` (active ones, in menu order) |
| `GET /services/search` *(signed in)* | *page* + `minPrice`, `maxPrice`; `q` = name, description, label; sort `order`, `name`, `price` | `Page<Service>` |
| `GET /services/{id}` | | `Service` |
| `GET /artists` | | `Artist[]` (active ones) |
| `GET /artists/search` *(signed in)* | *page* + `day` (works that weekday, e.g. `SATURDAY`); `q` = name, handle, specialty, bio; sort `order`, `name` | `Page<Artist>` |
| `GET /artists/{id}` | | `Artist` |
| `GET /availability` | `?serviceId&artistId` (leave `artistId` out for first available) `&from&days` (1–31, default 7) | `{ serviceId, artistId \| null, days: [{ date, slots: [{ time, startsAt, freeArtists }] }] }` |
| `POST /bookings` | `{ serviceId, artistId \| null, date, time, customerName, phone, email \| null, notes \| null }` | `201 Booking` (an email is sent if `email` is given). `429 TOO_MANY_SUBMISSIONS` after 10 bookings from one visitor in an hour (only bookings that went through count). Signed in as a customer (token)? It also goes to My bookings (a token that no longer works is ignored here: it becomes a guest booking) |
| `POST /bookings/lookup` | `{ code, phone }` | `Booking` |
| `POST /bookings/cancel` | `{ code, phone }` | `Booking` |
| `GET /reviews` | | `{ averageRating, count, reviews: Review[] }` (approved only) |
| `GET /reviews/search` *(signed in)* | *page* + `minRating` (1–5); `q` = name, text; sort `date`, `rating`, `name` | `Page<Review>` (approved only) |
| `POST /reviews` | `{ name, rating (1–5), text }` | `201` (shown after the admin approves it) |
| `POST /messages` | `{ name, contact, text }` | `201` |
| `GET /health` | | `{ status, database }` |

```ts
type Accent = "red" | "orange" | "yellow" | "green" | "cyan" | "purple"
type Service = { id: string; name: string; description: string; price: number; label: string; palette: string }
type WorkingHours = { day: "MONDAY" | … | "SUNDAY"; start: string; end: string }
type DayException = { date: string; closed: boolean; start: string | null; end: string | null; note: string | null }
type Artist = {
  id: string; name: string; handle: string; specialty: string; bio: string; photoUrl: string | null; accent: Accent
  weeklyHours: WorkingHours[]   // Monday first
  exceptions: DayException[]    // upcoming one-off days off / special hours
}
type Review = { id: string; name: string; rating: number; text: string; createdAt: string }
type BookingStatus = "BOOKED" | "CANCELLED" | "DONE" | "NO_SHOW"
type Booking = {
  code: string; status: BookingStatus; serviceName: string; artistId: string; artistName: string
  date: string; time: string; startsAt: string; endsAt: string; price: number
  customerName: string | null; phoneMasked: string | null; notes: string | null  // null once wiped (24 h after)
  cancellable: boolean; cancelDeadline: string
}
```

## Sign in (staff and customers)

| Method & path | Body | Response |
|---|---|---|
| `POST /auth/login` | `{ username, password }` (customers: their email as `username`) | `{ token, expiresAt, user: { username, role: "ADMIN" \| "ARTIST" \| "USER", artistId \| null, name \| null } }` |
| `POST /auth/register` | `{ name, phone, email, password (8+) }` | `201`, same as sign in (the new customer is signed in). `409 CONFLICT` if the email already has an account |
| `GET /auth/me` | | `{ username, role, artistId, name }` |

Send the token as `Authorization: Bearer <token>` on every `/staff/**`, `/admin/**` and `/account/**` call. It is valid for 12 hours. Usernames and emails are not case-sensitive. One visitor can create 5 accounts per hour (`429 TOO_MANY_SUBMISSIONS`). A token stops working as soon as its login is deleted or blocked, its password is reset, or its role or artist changes; a new account that reuses a deleted account's email never inherits the old tokens. One visitor gets 10 failed sign-ins per 15 minutes, and a username is blocked for 5 minutes after 5 wrong passwords.

## Customer account (`/account/**`, role USER)

| Method & path | Body | Response |
|---|---|---|
| `GET /account` | | `{ name, email, phone, createdAt }` |
| `GET /account/bookings` | *page* + `when` (`UPCOMING` default, `PAST`, `ALL`), `status`; `q` = service, code; sort `date`, `service`, `price`, `status` | `Page<Booking>`: bookings made while signed in (upcoming soonest first, otherwise latest first) |
| `POST /account/bookings/{code}/cancel` | | `Booking` (same 2-hour rule as `/bookings/cancel`; `404 BOOKING_NOT_FOUND` for someone else's booking) |
| `PUT /account/password` | `{ currentPassword, newPassword (8+) }` | A new sign-in (same as sign in); other devices are signed out. `400` if the current password is wrong |
| `POST /account/delete` | `{ password }` | `204`: account deleted; bookings stay for the shop without the account |

## Artist (`/staff/**`, the signed-in artist only)

| Method & path | Body / query | Response |
|---|---|---|
| `GET /staff/bookings` | *page* + `from`, `to`, `status` (default: last 7 days to 30 days ahead); `q` = customer, phone (as typed), email, code, service, notes; sort `date`, `service`, `customer`, `price`, `status` | `Page<StaffBooking>`, earliest first |
| `PATCH /staff/bookings/{id}` | `{ status: "DONE" \| "NO_SHOW" \| "CANCELLED" }` | `StaffBooking` |
| `GET /staff/schedule` | | `{ weeklyHours, exceptions, conflicts: StaffBooking[] }` (upcoming exceptions only) |
| `PUT /staff/schedule` | `{ weeklyHours, exceptions }` | same as GET (sorted; past dates dropped). Start times must be on the full hour |

`conflicts` lists upcoming booked appointments that fall outside the hours, e.g. after adding a day off. The hours are saved anyway; the bookings stay until someone cancels them.

Status rules (also for the admin): *Done* / *No-show* only once the appointment has started, and switching between the two corrects a mistake. *Cancel* works on booked appointments at any time and emails the customer if the appointment is still ahead. A cancelled booking stays cancelled.

```ts
type StaffBooking = {
  id: string; code: string; status: BookingStatus; serviceName: string; artistId: string; artistName: string
  date: string; time: string; startsAt: string; endsAt: string; price: number
  customerName: string | null; phone: string | null; email: string | null; notes: string | null
  anonymized: boolean
  awaitingStatus: boolean   // BOOKED but already over: mark it Done or No-show
}
```

## Admin (`/admin/**`)

| Method & path | Body / query | Response |
|---|---|---|
| `GET /admin/bookings` | *page* + `artistId`, `from`, `to`, `status`; `q` and sort as for `/staff/bookings` | `Page<StaffBooking>` |
| `PATCH /admin/bookings/{id}` | `{ status }` | `StaffBooking` |
| `PUT /admin/bookings/{id}` | `{ customerName, phone, email \| null, notes \| null }` | `StaffBooking` (time, artist and service stay; `409` once the details were wiped for privacy) |
| `DELETE /admin/bookings/{id}` | | `204`: gone for good, no email (cancel instead to tell the customer) |
| `GET /admin/stats` | `?from&to` (default: this week, Monday to Sunday) | `Stats` |
| `GET /admin/services` | *page* + `active`, `minPrice`, `maxPrice`; `q` = name, description, label; sort `order`, `name`, `price` | `Page<AdminService>` (including hidden ones) |
| `POST /admin/services` | `{ name, description, price, label, palette }` | `201 AdminService` |
| `PUT /admin/services/{id}` | same, plus optional `active` (`false` hides it from the booking page) | `AdminService` |
| `PATCH /admin/services/{id}` | `{ active }`: shows or hides it, nothing else changes | `AdminService` |
| `DELETE /admin/services/{id}` | | `204`: deleted for good (bookings keep their copy of name and price). `409` while it has upcoming bookings |
| `GET /admin/artists` | *page* + `active`, `day`; `q` = name, handle, specialty, bio; sort `order`, `name` | `Page<AdminArtist>` (including inactive ones) |
| `POST /admin/artists` | `{ name, handle, specialty, bio, photoUrl, accent, weeklyHours, exceptions }` | `201 AdminArtist` |
| `GET /admin/artists/options` | | `ArtistOption[]`: every artist (inactive too) by name, `{ id, name, active, username }`, for pickers |
| `PUT /admin/artists/{id}` | same, plus optional `active` (`false` deactivates them and blocks their login; `409` while they have upcoming bookings) | `AdminArtist` |
| `PATCH /admin/artists/{id}` | `{ active }`: (de)activates them, hours stay as they are (`409` while they have upcoming bookings) | `AdminArtist` |
| `DELETE /admin/artists/{id}` | | `204`: deletes the artist and their login. Only for artists without any bookings (`409` otherwise: deactivate them instead) |
| `POST /admin/artists/{id}/login` | `{ username (3–30 of a-z 0-9 . _ -), password (8+) }` | `201 { username }`: creates or resets their one login (signs out the old sessions; a login blocked under *Users & rights* stays blocked) |
| `GET /admin/reviews` | *page* + `status=pending\|approved` (all if left out), `minRating`; `q` = name, text; sort `date`, `rating`, `name` | `Page<AdminReview>`, newest first |
| `PATCH /admin/reviews/{id}` | `{ approved }` | `AdminReview` |
| `PUT /admin/reviews/{id}` | `{ name, rating, text, approved }` | `AdminReview` |
| `DELETE /admin/reviews/{id}` | | `204` |
| `GET /admin/messages` | *page* + `read` (true / false); `q` = name, contact, text; sort `date`, `name` | `Page<Message>`, newest first (deleted automatically after 90 days) |
| `PATCH /admin/messages/{id}` | `{ read }` | `Message` |
| `DELETE /admin/messages/{id}` | | `204` |
| `GET /admin/users` | *page* + `role` (`USER`, `ARTIST`, `ADMIN`), `active`; `q` = username, name, email, phone; sort `username`, `name`, `role`, `created`, `lastLogin` | `Page<AdminUser>` |
| `POST /admin/users` | `{ role, password (8+), username (staff), artistId (artists), name, email, phone (customers) }` | `201 AdminUser` |
| `PUT /admin/users/{id}` | `{ role, active, artistId, name, phone }` | `AdminUser`. A change of role or artist signs them out. Refused (`409`) for your own login and for the last active admin |
| `PUT /admin/users/{id}/password` | `{ password (8+) }` | `204`, signs them out everywhere |
| `DELETE /admin/users/{id}` | | `204` (not yourself, not the last admin). A customer's bookings stay, without the account |

```ts
type AdminService = Service & { sortOrder: number; active: boolean }
type AdminArtist = Artist & {
  sortOrder: number; active: boolean; username: string | null
  conflicts: StaffBooking[]  // upcoming bookings outside their hours
}
type ArtistOption = { id: string; name: string; active: boolean; username: string | null }
type AdminReview = Review & { approved: boolean }
type AdminUser = {
  id: string; username: string; role: "USER" | "ARTIST" | "ADMIN"; active: boolean
  name: string | null; email: string | null; phone: string | null   // customers
  artistId: string | null; artistName: string | null                 // artists
  createdAt: string | null; lastLoginAt: string | null; loginCount: number
}
type Message = {
  id: string; name: string; contact: string; text: string; read: boolean; createdAt: string
  senderMessageNumber: number   // 1st, 2nd, … message from this email / phone (any phone format, any email case)
  readAt: string | null
}
type Stats = {
  from: string; to: string
  bookings: number          // not cancelled
  done: number; noShows: number; cancelled: number
  revenue: number           // DONE bookings only, at the price when booked
  busiestArtist: { id: string; name: string; bookings: number } | null
  perArtist: { artistId: string; name: string; bookings: number; done: number; noShows: number; revenue: number }[]
}
```
