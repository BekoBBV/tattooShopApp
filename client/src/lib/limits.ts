/**
 * The longest (and shortest) allowed value of every text field, the same numbers the server
 * checks (its request records in server/.../dto). Forms use these for maxLength / minLength, and
 * the server test LimitsSyncTest fails if a number here and on the server ever differ.
 */
export const limits = {
  // Booking (customer details step, admin edit)
  "booking.customerName": 60,
  "booking.phone": 30,
  "booking.email": 120,
  "booking.notes": 300,
  // Manage booking (code + phone)
  "lookup.code": 20,
  "lookup.phone": 30,
  // Customer account
  "account.name": 60,
  "account.phone": 30,
  "account.email": 120,
  "account.passwordMin": 8,
  "account.password": 200,
  // Sign in
  "login.username": 120,
  "login.password": 200,
  // Reviews and contact messages
  "review.name": 60,
  "review.text": 500,
  "message.name": 60,
  "message.contact": 120,
  "message.text": 1000,
  // Admin: services, artists, logins
  "service.name": 60,
  "service.description": 300,
  "service.label": 12,
  "artist.name": 60,
  "artist.handle": 40,
  "artist.specialty": 60,
  "artist.bio": 500,
  "artist.photoUrl": 500,
  "artistLogin.username": 30,
  "user.username": 30,
  "user.name": 60,
  "user.email": 120,
  "user.phone": 30,
  "user.passwordMin": 8,
  "user.password": 200,
  // Artist hours: note on a day off / special day
  "exception.note": 100,
  // Lists
  "search.q": 100,
} as const

export type LimitKey = keyof typeof limits
