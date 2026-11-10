import { Check, LogOut, X } from "lucide-react"
import { useEffect, useRef, useState, type ReactNode } from "react"
import { Link, Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router"
import { clearSession } from "../lib/api"
import { useStaff } from "../lib/staff"
import { errorText } from "../lib/useLoad"
import { Dialog, FormError } from "./Studio"

const ARTIST_NAV = [
  { to: "/staff/calendar", label: "My calendar" },
  { to: "/staff/hours", label: "My hours" },
  { to: "/staff/flash", label: "My flash and work" },
]

const ADMIN_NAV = [
  { to: "/admin/bookings", label: "Bookings" },
  { to: "/admin/flash", label: "Flash catalogue" },
  { to: "/admin/artists", label: "Artists" },
  { to: "/admin/reviews", label: "Reviews" },
  { to: "/admin/messages", label: "Messages" },
  { to: "/admin/users", label: "Users" },
]

/**
 * The artist and admin area: the sidebar (a top bar with scrolling tabs on phones) and the page.
 * Signed-out visitors go to the staff sign-in; artists stay in /staff and the admin in /admin.
 */
export function StaffLayout() {
  const staff = useStaff()
  const location = useLocation()
  const navigate = useNavigate()
  if (!staff) return <Navigate to="/staff/sign-in" replace />
  const isAdmin = staff.user.role === "ADMIN"
  const inAdmin = location.pathname.startsWith("/admin")
  if (isAdmin && !inAdmin) return <Navigate to="/admin/bookings" replace />
  if (!isAdmin && inAdmin) return <Navigate to="/staff/calendar" replace />
  const nav = isAdmin ? ADMIN_NAV : ARTIST_NAV
  const area = isAdmin ? "Administration" : "Artist area"

  return (
    <div className="staff-app">
      <aside className="staff-sidebar">
        <div className="staff-sidebar-heading">
          <Link to="/" aria-label="Blade and Brush site" className="staff-home-link">
            <img src="/images/seal-bb.png" alt="" width="48" height="48" />
          </Link>
          <div>
            <strong>{staff.user.name ?? (isAdmin ? "Studio owner" : staff.user.username)}</strong>
            <span>{area}</span>
          </div>
        </div>
        <nav aria-label={area}>
          {nav.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => (isActive ? "is-active" : "")}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <button
          className="staff-sign-out"
          type="button"
          onClick={() => {
            clearSession()
            navigate("/staff/sign-in", { replace: true })
          }}
        >
          <LogOut className="icon" size={18} aria-hidden="true" /> Sign out
        </button>
      </aside>
      <main className="staff-main">
        <Outlet />
      </main>
    </div>
  )
}

/** The page title, a line under it and the page's main button. */
export function StaffHeader({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <header className="staff-page-header">
      <div>
        <h1>{title}</h1>
        {children && <p>{children}</p>}
      </div>
      {action}
    </header>
  )
}

/** The square on / off switch (a real checkbox underneath). */
export function StaffSwitch({
  checked,
  onChange,
  label,
  disabled = false,
  hiddenLabel,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  disabled?: boolean
  /** Fuller words for screen readers, e.g. "Show Mira's review on site". */
  hiddenLabel?: string
}) {
  return (
    <label className={`staff-switch${disabled ? " is-disabled" : ""}`}>
      <input type="checkbox" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} aria-label={hiddenLabel} />
      <span className={`switch-track${checked ? " is-on" : ""}`} aria-hidden="true">
        <i></i>
      </span>
      <strong aria-hidden={hiddenLabel ? true : undefined}>{label}</strong>
    </label>
  )
}

export function NightStamp({ tone, children }: { tone: "paid" | "waiting" | "muted" | "plain"; children: ReactNode }) {
  return <span className={`night-stamp${tone === "plain" ? "" : ` is-${tone}`}`}>{children}</span>
}

/** A short note after something was saved (or went wrong), at the top of the page. */
export function StatusLine({ children, error = false }: { children: ReactNode; error?: boolean }) {
  if (!children) return null
  return (
    <p className={`status-line${error ? " is-error" : ""}`} role={error ? "alert" : "status"}>
      {!error && <Check className="icon" size={18} aria-hidden="true" />}
      {children}
    </p>
  )
}

/**
 * A panel that slides in from the right over the dimmed page (full width under the top bar on
 * phones). Focus moves into it and back when it closes; Escape closes it.
 */
export function SidePanel({ title, label, onClose, children }: { title: string; label: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLElement | null>(null)
  const close = useRef(onClose)
  useEffect(() => {
    close.current = onClose
  })
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    ref.current?.querySelector<HTMLElement>("button, input, select, textarea, a")?.focus()
    const onKey = (event: KeyboardEvent) => {
      // A dialog opened from the panel handles its own Escape
      if (event.key === "Escape" && !document.querySelector(".dialog-backdrop")) close.current()
    }
    document.addEventListener("keydown", onKey)
    return () => {
      document.removeEventListener("keydown", onKey)
      previous?.focus()
    }
  }, [])
  return (
    <>
      <div className="panel-backdrop" aria-hidden="true" onClick={onClose}></div>
      <aside
        ref={(el) => {
          ref.current = el
        }}
        className="side-panel on-paper"
        role="dialog"
        aria-modal="true"
        aria-label={label}
      >
        <div className="side-panel-heading">
          <h2>{title}</h2>
          <button className="icon-square" type="button" aria-label="Close panel" onClick={onClose}>
            <X className="icon" size={20} aria-hidden="true" />
          </button>
        </div>
        {children}
      </aside>
    </>
  )
}

/** "Are you sure?" with the safe choice focused; shows the server's answer if it fails. */
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  keepLabel = "Keep it",
  onConfirm,
  onClose,
}: {
  title: string
  children: ReactNode
  confirmLabel: string
  keepLabel?: string
  onConfirm: () => Promise<void>
  onClose: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  return (
    <Dialog title={title} titleId="confirm-title" onClose={onClose}>
      <p>{children}</p>
      <FormError>{problem}</FormError>
      <div className="dialog-actions">
        <button
          className="button button-primary"
          type="button"
          disabled={busy}
          onClick={async () => {
            setBusy(true)
            setProblem("")
            try {
              await onConfirm()
            } catch (reason) {
              setProblem(errorText(reason))
              setBusy(false)
            }
          }}
        >
          {busy ? "One moment..." : confirmLabel}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose} data-autofocus>
          {keepLabel}
        </button>
      </div>
    </Dialog>
  )
}

/** Previous / next under a long list. */
export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav className="staff-pager" aria-label="Pages">
      <button className="staff-text-button" type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button className="staff-text-button" type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Next
      </button>
    </nav>
  )
}

/** Loading, empty and error states inside a staff page. */
export function StaffState({ loading, error, empty, onRetry }: { loading: boolean; error: string; empty?: string | false | null; onRetry?: () => void }) {
  if (error)
    return (
      <p className="staff-note" role="alert">
        {error}{" "}
        {onRetry && (
          <button className="staff-text-button" type="button" onClick={onRetry}>
            Try again
          </button>
        )}
      </p>
    )
  if (loading) return <p className="staff-note">Loading...</p>
  if (empty) return <p className="staff-note">{empty}</p>
  return null
}
