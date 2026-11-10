import { useEffect, useMemo, useSyncExternalStore } from "react"
import { api, getSession, isSessionExpired, STAFF_CHANGED_EVENT } from "./api"
import type { LoginResponse } from "./types"

let cachedRaw: string | null = null
let cached: LoginResponse | null = null

/** The signed-in artist or admin, or null (signed out, expired, or a customer token). */
function current(): LoginResponse | null {
  const session = getSession()
  const raw = session ? JSON.stringify(session) : null
  if (raw !== cachedRaw) {
    cachedRaw = raw
    cached = session
  }
  if (!cached || isSessionExpired(cached) || cached.user.role === "USER") return null
  return cached
}

function subscribe(onChange: () => void) {
  window.addEventListener(STAFF_CHANGED_EVENT, onChange)
  window.addEventListener("storage", onChange) // signed in or out in another tab
  return () => {
    window.removeEventListener(STAFF_CHANGED_EVENT, onChange)
    window.removeEventListener("storage", onChange)
  }
}

/** The signed-in staff member; the component updates when they sign in or out. */
export function useStaff() {
  return useSyncExternalStore(subscribe, current, () => null)
}

/** A call to the API with the staff token (a 401 signs them out, see api()). */
export function staffApi<T>(path: string, options: RequestInit = {}) {
  return api<T>(path, { ...options, token: current()?.token })
}

/** A JSON body for staffApi. */
export const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) })

/** A multipart body: `data` as a JSON part (if given) next to the picture parts. */
export function multipart(method: string, files: Record<string, File | null | undefined>, data?: { name: string; value: unknown }) {
  const form = new FormData()
  if (data) form.append(data.name, new Blob([JSON.stringify(data.value)], { type: "application/json" }))
  for (const [name, file] of Object.entries(files)) if (file) form.append(name, file)
  return { method, body: form }
}

/** The time pickers' choices: every half hour from 08:00 to 23:30 (the API's 30-minute grid). */
export const halfHours = Array.from({ length: 32 }, (_, i) => {
  const minutes = 8 * 60 + i * 30
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${minutes % 60 ? "30" : "00"}`
})

/** Monday of the week that `date` ("YYYY-MM-DD") is in. */
export function mondayOf(date: string) {
  const d = new Date(`${date}T12:00:00Z`)
  const back = (d.getUTCDay() + 6) % 7
  d.setUTCDate(d.getUTCDate() - back)
  return d.toISOString().slice(0, 10)
}

/** `date` plus `days`, "YYYY-MM-DD". */
export function addDays(date: string, days: number) {
  const d = new Date(`${date}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** The weekday of a date as the API names it. */
export function weekdayOf(date: string) {
  return ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"][
    new Date(`${date}T12:00:00Z`).getUTCDay()
  ] as "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY"
}

/** The pictures the server takes (it checks the first bytes too): JPG, PNG, HEIC or WebP, 10 MB at most. */
export const PICTURE_TYPES = "image/jpeg,image/png,image/webp,image/heic,image/heif,.heic,.heif"

/** Why a picture can't be uploaded, or null when it can. */
export function pictureProblem(file: File) {
  if (file.size > 10 * 1024 * 1024) return `${file.name} is larger than 10 MB.`
  if (!/\.(jpe?g|png|webp|heic|heif)$/i.test(file.name) && !/^image\/(jpeg|png|webp|heic|heif)$/.test(file.type))
    return `${file.name} isn't a JPG, PNG, HEIC or WebP picture.`
  return null
}

/** How many mounted components use each preview address. */
const previewUsers = new Map<string, number>()

/**
 * Preview addresses for chosen pictures, released again when the pictures change or the dialog
 * closes. The release waits a moment, so React's development double-run (unmount, mount again)
 * doesn't release a preview that is still on screen.
 */
export function usePreviewUrls(files: (File | null)[]) {
  const urls = useMemo(() => files.map((f) => (f ? URL.createObjectURL(f) : null)), [files])
  useEffect(() => {
    const own = urls.filter((u): u is string => !!u)
    own.forEach((u) => previewUsers.set(u, (previewUsers.get(u) ?? 0) + 1))
    return () =>
      own.forEach((u) => {
        previewUsers.set(u, (previewUsers.get(u) ?? 1) - 1)
        window.setTimeout(() => {
          if (previewUsers.get(u)) return
          previewUsers.delete(u)
          URL.revokeObjectURL(u)
        }, 0)
      })
  }, [urls])
  return urls
}
