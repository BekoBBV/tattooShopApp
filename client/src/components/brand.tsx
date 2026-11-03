import { Menu, Scissors, X } from "lucide-react"
import { useState, type ReactNode } from "react"
import { useCustomer } from "../lib/customer"
import { AppLink, Button } from "./ui"

export function SprayDisc({
  accent = "cyan",
  initials,
  size = "md",
}: {
  accent?: string
  initials?: string
  size?: "sm" | "md" | "lg"
}) {
  return (
    <div
      className={`spray-disc spray-disc-${size} accent-${accent}`}
      aria-hidden={!initials}
    >
      <span className="spray-disc-ring">
        <span className="spray-disc-nozzle" />
      </span>
      <span className="spray-disc-cap">{initials}</span>
    </div>
  )
}

export function MiniDisc({
  accent = "cyan",
  label,
  children,
  selected = false,
  className = "",
}: {
  accent?: string
  label?: string
  children: ReactNode
  selected?: boolean
  className?: string
}) {
  return (
    <div
      className={`minidisc accent-${accent} ${
        selected ? "is-selected" : ""
      } ${className}`}
    >
      <span className="minidisc-notch" aria-hidden="true" />
      {label ? <span className="minidisc-label">{label}</span> : null}
      {children}
    </div>
  )
}

export function SiteHeader() {
  const [open, setOpen] = useState(false)
  const customer = useCustomer()
  return (
    <header className="site-header">
      <AppLink to="/" className="brand" aria-label="Tattoo Studio home">
        <Scissors className="size-5" />
        TATTOO <span>STUDIO</span>
      </AppLink>
      <nav
        className={`site-nav ${open ? "is-open" : ""}`}
        aria-label="Main navigation"
      >
        <AppLink to="/#services" onClick={() => setOpen(false)}>
          Services
        </AppLink>
        <AppLink to="/#artists" onClick={() => setOpen(false)}>
          Artists
        </AppLink>
        <AppLink to="/cancel" onClick={() => setOpen(false)}>
          Manage booking
        </AppLink>
        {customer ? (
          <AppLink
            to="/account"
            className="nav-account"
            onClick={() => setOpen(false)}
          >
            My account
          </AppLink>
        ) : (
          <AppLink to="/account/sign-in" onClick={() => setOpen(false)}>
            Sign in
          </AppLink>
        )}
        <AppLink to="/staff/login" onClick={() => setOpen(false)}>
          Staff
        </AppLink>
        <AppLink
          to="/book/service"
          variant="primary"
          onClick={() => setOpen(false)}
        >
          Book a cut
        </AppLink>
      </nav>
      <Button
        className="menu-button"
        variant="ghost"
        aria-label={open ? "Close menu" : "Open menu"}
        onClick={() => setOpen(!open)}
      >
        {open ? <X /> : <Menu />}
      </Button>
    </header>
  )
}

export function SiteFooter() {
  return (
    <footer className="footer">
      <div className="wrap">
        <span className="brand">
          FADE <b>STREET</b>
        </span>
        <p>Original cuts. Plovdiv energy.</p>
        <div className="footer-links">
          <AppLink to="/cancel">Manage booking</AppLink>
          <AppLink to="/staff/login">Staff login</AppLink>
        </div>
      </div>
    </footer>
  )
}

export function GraffitiBackdrop() {
  return (
    <div className="graffiti" aria-hidden="true">
      <span>FS</span>
      <span>STAY SHARP</span>
      <span>PDV</span>
      <span>14</span>
      <span>FRESH</span>
    </div>
  )
}
