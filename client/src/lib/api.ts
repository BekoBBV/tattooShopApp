import { getCustomer, setCustomer } from "./customer"
import type { ApiErrorBody, ErrorCode, LoginResponse } from "./types"

/**
 * Talks to the Spring Boot API. In development Vite forwards /api to
 * http://localhost:8080 (see vite.config.ts). Pages call e.g. api("/services").
 */
const BASE_URL = "/api"

/** An error answer from the API. `detail` (the message) is shown to the user. */
export class ApiError extends Error {
  status: number
  code?: ErrorCode
  errors?: Record<string, string>
  constructor(body: ApiErrorBody) {
    super(body.detail)
    this.name = "ApiError"
    this.status = body.status
    this.code = body.code
    this.errors = body.errors
  }
}

const sessionKey = "blade-and-brush-staff-session"

/** The signed-in staff member, if any (kept in this browser only). */
export const getSession = (): LoginResponse | null => {
  try {
    return JSON.parse(
      localStorage.getItem(sessionKey) || "null",
    ) as LoginResponse | null
  } catch {
    return null
  }
}

export const clearSession = () => {
  try {
    localStorage.removeItem(sessionKey)
  } catch {
    // Storage blocked: nothing to clear
  }
  window.dispatchEvent(new Event(STAFF_CHANGED_EVENT))
}

/** Fired when a staff member signs in or out on this tab (lib/staff.ts listens). */
export const STAFF_CHANGED_EVENT = "blade-and-brush-staff-changed"

/** Fired when the staff token is missing or expired, so the dashboard can send the user to sign in. */
export const SESSION_EXPIRED_EVENT = "blade-and-brush-session-expired"

/** True if the stored token has passed its expiry time. */
export const isSessionExpired = (session: LoginResponse) =>
  !session.expiresAt || new Date(session.expiresAt).getTime() <= Date.now()

/** True when an artist or admin is signed in on this browser. */
export const staffSignedIn = () => {
  const session = getSession()
  return !!session && !isSessionExpired(session) && session.user.role !== "USER"
}

/** Saves a staff sign-in (customers use lib/customer.ts). */
export const saveSession = (session: LoginResponse) => {
  try {
    localStorage.setItem(sessionKey, JSON.stringify(session))
  } catch {
    // Storage blocked: the user stays signed in until the page reloads
  }
  window.dispatchEvent(new Event(STAFF_CHANGED_EVENT))
}

export async function api<T>(
  path: string,
  options: RequestInit & { token?: string } = {},
): Promise<T> {
  const { token, headers, ...init } = options
  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path.replace(/^\/api/, "")}`, {
      ...init,
      headers: {
        Accept: "application/json",
        // A FormData body (pictures) sets its own multipart type with the boundary
        ...(init.body && !(init.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
    })
  } catch {
    throw new ApiError({
      status: 0,
      title: "Network error",
      detail: "Can't reach Blade & Brush right now. Check your connection and try again.",
    })
  }

  const text = await response.text()
  const body = text ? safeJson(text) : undefined

  if (!response.ok) {
    if (response.status === 401 && token) {
      // Sign out whoever this token belonged to (staff member or customer)
      if (getSession()?.token === token) {
        clearSession()
        window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
      }
      if (getCustomer()?.token === token) setCustomer(null)
    }
    const problem = body as Partial<ApiErrorBody> | undefined
    throw new ApiError({
      status: response.status,
      title: problem?.title || response.statusText,
      code: problem?.code,
      detail:
        problem?.detail ||
        (response.status === 401
          ? "Please sign in again."
          : "Something went wrong. Please try again."),
      errors: problem?.errors,
    })
  }

  return body as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}
