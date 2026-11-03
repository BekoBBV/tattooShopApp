import { useSyncExternalStore } from "react"
import { getSession, isSessionExpired } from "./api"
import type { LoginResponse } from "./types"

/**
 * The signed-in customer (role USER), kept in this browser only and separate from the staff
 * sign-in, so an artist testing the site as a customer never loses their staff session.
 */
const key = "tattoo-studio-customer-session"
const CHANGED = "tattoo-studio-customer-changed"

/** Used when the browser blocks storage: then the sign-in lasts until the page reloads. */
let memory: string | null = null

function read(): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return memory
  }
}

let cachedRaw: string | null = null
let cached: LoginResponse | null = null

/** The customer session, or null when signed out or the token has expired. */
export function getCustomer(): LoginResponse | null {
  const raw = read()
  if (raw !== cachedRaw) {
    cachedRaw = raw
    try {
      cached = raw ? (JSON.parse(raw) as LoginResponse) : null
    } catch {
      cached = null
    }
  }
  if (cached && new Date(cached.expiresAt).getTime() <= Date.now()) return null
  return cached
}

/** Signs a customer in (session) or out (null). */
export function setCustomer(session: LoginResponse | null) {
  memory = session ? JSON.stringify(session) : null
  try {
    if (memory) localStorage.setItem(key, memory)
    else localStorage.removeItem(key)
  } catch {
    // Storage blocked: kept in memory instead
  }
  window.dispatchEvent(new Event(CHANGED))
}

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGED, onChange)
  window.addEventListener("storage", onChange) // signed in or out in another tab
  return () => {
    window.removeEventListener(CHANGED, onChange)
    window.removeEventListener("storage", onChange)
  }
}

/** The signed-in customer; the component updates when they sign in or out. */
export function useCustomer() {
  return useSyncExternalStore(subscribe, getCustomer, () => null)
}

/**
 * The token of whoever is signed in on this browser (customer first, then staff), or undefined
 * for a guest. Signed-in visitors may search, sort and page the public lists.
 */
export function useViewerToken(): string | undefined {
  const customer = useCustomer()
  if (customer) return customer.token
  const staff = getSession()
  return staff && !isSessionExpired(staff) ? staff.token : undefined
}
