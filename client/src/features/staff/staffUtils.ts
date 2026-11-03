import { useEffect, useState } from "react"
import { ApiError, api, getSession } from "../../lib/api"
import type { Page } from "../../lib/paging"
import { errorText, useLoad } from "../../lib/useLoad"
import type { Accent, BookingStatus, Day, StaffBooking } from "../../lib/types"

/** Helpers shared by the staff and admin screens: dates in shop time, loading and actions. */
export const dayNames: Record<Day, string> = {
  MONDAY: "Monday",
  TUESDAY: "Tuesday",
  WEDNESDAY: "Wednesday",
  THURSDAY: "Thursday",
  FRIDAY: "Friday",
  SATURDAY: "Saturday",
  SUNDAY: "Sunday",
}
export const allDays = Object.keys(dayNames) as Day[]
export const accents: Accent[] = ["red", "orange", "yellow", "green", "cyan", "purple"]
export const palettes = ["blue", "sunset", "lime", "mint", "violet"]
const SHOP_ZONE = "Europe/Sofia"

export const token = () => getSession()?.token || ""
export const initials = (name: string) =>
  name
    .split(/[\s._-]+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()

/** "1 booking", "2 bookings" */
export const plural = (count: number, word: string) =>
  `${count} ${word}${count === 1 ? "" : "s"}`

// ---- Dates (always in shop time) ----

/** Today's date in the shop, as YYYY-MM-DD. */
export const shopToday = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: SHOP_ZONE }).format(new Date())
export const addDays = (date: string, days: number) => {
  const d = new Date(`${date}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}
export const mondayOf = (date: string) => {
  const weekday = new Date(`${date}T12:00:00Z`).getUTCDay() // 0 = Sunday
  return addDays(date, -((weekday + 6) % 7))
}
export const formatDay = (date: string) =>
  new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(`${date}T12:00:00Z`))
export const formatDateTime = (iso: string) =>
  new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: SHOP_ZONE,
  }).format(new Date(iso))
export const formatTime = (iso: string) =>
  new Intl.DateTimeFormat("en-GB", {
    timeStyle: "short",
    timeZone: SHOP_ZONE,
  }).format(new Date(iso))


/** Loads `path` with the staff token (`null` waits); `reload()` fetches it again. */
export function useStaffLoad<T>(path: string | null) {
  return useLoad<T>(path, token())
}

/** The current time, refreshed every minute (e.g. so Done buttons appear once an appointment starts). */
export function useNow() {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 60_000)
    return () => window.clearInterval(id)
  }, [])
  return now
}

/** Runs one button or form action at a time and keeps its error / success message. */
export function useAction() {
  const [busy, setBusy] = useState("")
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  async function run(
    key: string,
    action: () => Promise<unknown>,
    success = "",
  ): Promise<boolean> {
    setBusy(key)
    setError("")
    setNotice("")
    setFieldErrors({})
    try {
      await action()
      setNotice(success)
      return true
    } catch (reason) {
      setError(errorText(reason))
      if (reason instanceof ApiError) setFieldErrors(reason.errors || {})
      return false
    } finally {
      setBusy("")
    }
  }
  const clear = () => {
    setError("")
    setNotice("")
    setFieldErrors({})
  }
  return { busy, error, notice, fieldErrors, run, clear }
}

export const send = (path: string, method: string, body?: unknown) =>
  api(path, {
    method,
    token: token(),
    body: body === undefined ? undefined : JSON.stringify(body),
  })

// ---- Bookings (shared by artists and the admin) ----

/** Asks before cancelling; other status changes need no confirmation. */
export function confirmStatusChange(booking: StaffBooking, status: BookingStatus) {
  return (
    status !== "CANCELLED" ||
    window.confirm(
      `Cancel ${booking.customerName || "this booking"} on ${formatDay(booking.date)} at ${booking.time}?` +
        (booking.email ? " The customer gets an email." : ""),
    )
  )
}

/** Marks a booking Done / No-show / Cancelled as the artist or the admin. */
export const changeBookingStatus = (
  booking: StaffBooking,
  status: BookingStatus,
  admin: boolean,
) =>
  send(`${admin ? "/admin" : "/staff"}/bookings/${booking.id}`, "PATCH", {
    status,
  })

/** One page of bookings from `path` (a staff or admin list) plus the Done / No-show / Cancel action. */
export function useBookings(path: string, admin: boolean) {
  const load = useStaffLoad<Page<StaffBooking>>(path)
  const action = useAction()
  const update = (booking: StaffBooking, status: BookingStatus) => {
    if (!confirmStatusChange(booking, status)) return
    action
      .run(booking.id + status, () => changeBookingStatus(booking, status, admin))
      .then((ok) => ok && load.reload())
  }
  return { ...load, bookings: load.data?.items || [], update, action }
}
