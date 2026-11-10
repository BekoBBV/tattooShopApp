import { ChevronRight, Menu, Phone, X } from "lucide-react"
import { useEffect, useId, useState, type ReactNode } from "react"
import { Link, NavLink, Outlet, ScrollRestoration, useLocation } from "react-router"
import { studio } from "../lib/format"

const NAV = [
  { to: "/flash", label: "Flash", menuLabel: "Flash designs" },
  { to: "/artists", label: "Artists", menuLabel: "Artists" },
  { to: "/aftercare", label: "Aftercare", menuLabel: "Aftercare" },
  { to: "/manage", label: "Manage a booking", menuLabel: "Manage a booking" },
  { to: "/account", label: "Account", menuLabel: "Account" },
]

/**
 * The header on every customer page: the seal and wordmark, the links (on tablets and phones they
 * move into the menu) and the red Book button.
 */
export function SiteHeader() {
  const location = useLocation()
  // The menu is open for the page it was opened on, so going to another page closes it
  const [openOn, setOpenOn] = useState<string | null>(null)
  const open = openOn === location.pathname
  const setOpen = (next: boolean | ((was: boolean) => boolean)) =>
    setOpenOn((was) => ((typeof next === "function" ? next(was === location.pathname) : next) ? location.pathname : null))

  // Escape closes it too; the page behind doesn't scroll while it is open
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpenOn(null)
    document.addEventListener("keydown", onKey)
    document.body.style.overflow = "hidden"
    return () => {
      document.removeEventListener("keydown", onKey)
      document.body.style.overflow = ""
    }
  }, [open])

  return (
    <>
      <header className="site-header">
        <Link to="/" aria-label="Blade and Brush home" className="logo-link" style={{ textDecoration: "none" }}>
          <span className="seal-lockup">
            <img className="seal real-seal" src="/images/seal-bb.png" alt="" width="44" height="44" style={{ width: 44, height: 44 }} />
            <span className="wordmark">Blade &amp; Brush</span>
          </span>
        </Link>
        <nav className="desktop-nav" aria-label="Main navigation">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="header-actions" style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Link className="button button-primary compact" to="/book/choose">
            Book
          </Link>
          <button
            className="menu-button"
            type="button"
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
            aria-controls="phone-menu"
            onClick={() => setOpen((o) => !o)}
          >
            {open ? <X className="icon" size={22} aria-hidden="true" /> : <Menu className="icon" size={22} aria-hidden="true" />}
          </button>
        </div>
      </header>
      <nav className={`phone-menu${open ? " is-open" : ""}`} id="phone-menu" aria-label="Main navigation" hidden={!open}>
        {NAV.map((item) => (
          <Link key={item.to} to={item.to}>
            {item.menuLabel}
            <ChevronRight className="icon" size={22} aria-hidden="true" />
          </Link>
        ))}
        <div className="phone-menu-art" aria-hidden="true">
          <img src="/images/footer-night.jpg" alt="" />
        </div>
        <div className="phone-menu-footer">
          <p>
            {studio.hours}
            <br />
            {studio.closed}
          </p>
          <a className="button button-secondary" href={studio.tel}>
            <Phone className="icon" size={18} aria-hidden="true" />
            {studio.phone}
          </a>
        </div>
      </nav>
    </>
  )
}

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="footer-river" aria-hidden="true"></div>
      <div className="footer-inner">
        <p className="footer-name" aria-hidden="true">
          Blade &amp; Brush
        </p>
        <div className="footer-grid">
          <div className="footer-brand">
            <img className="footer-seal" src="/images/seal-bb.png" alt="" />
            <p>Traditional Japanese tattooing in Sofia.</p>
          </div>
          <div>
            <h2>Visit</h2>
            <p>
              {studio.address}
              <br />
              {studio.hours}
              <br />
              {studio.closed}
            </p>
          </div>
          <div>
            <h2>Contact</h2>
            <p>
              <a href={studio.tel}>{studio.phone}</a>
              <br />
              <Link to="/#contact">Write to the studio</Link>
            </p>
          </div>
          <nav aria-label="Footer navigation">
            <h2>Bookings</h2>
            <Link to="/manage">Manage a booking</Link>
            <Link to="/aftercare">Aftercare</Link>
            <Link to="/staff/sign-in">Staff sign-in</Link>
          </nav>
        </div>
        <p className="copyright">
          <span>Blade &amp; Brush is a concept studio.</span>
          <Link to="/credits">Image credits</Link>
        </p>
      </div>
    </footer>
  )
}

/** The slim footer of the booking steps, so nothing pulls people away mid-booking. */
export function CompactFooter() {
  return (
    <footer className="compact-footer">
      <div className="wrap compact-footer-inner">
        <span className="compact-lockup">
          <img src="/images/seal-bb.png" alt="" width="36" height="36" />
          <span className="wordmark">Blade &amp; Brush</span>
        </span>
        <p>
          Need help with your booking? Call <a href={studio.tel}>{studio.phone}</a> or{" "}
          <Link to="/#contact">write to the studio</Link>.
        </p>
        <Link to="/manage">Manage a booking</Link>
      </div>
    </footer>
  )
}

/** Header, page and the full footer: every customer page except the booking steps. */
export function SiteLayout() {
  return (
    <div>
      <SiteHeader />
      <Outlet />
      <SiteFooter />
    </div>
  )
}

/** The booking steps: header, page and the slim footer. */
export function BookingLayout() {
  return (
    <div>
      <SiteHeader />
      <Outlet />
      <CompactFooter />
    </div>
  )
}

/**
 * Around every page: new pages start at the top, Back returns to where you were, and links like
 * /#contact scroll to their section (React Router's ScrollRestoration does all three).
 */
export function RootLayout() {
  return (
    <>
      <ScrollRestoration />
      <Outlet />
    </>
  )
}

/** The strip of the night painting and the page title under it (every page but the home page). */
export function PageIntro({ title, children, stroke = true }: { title: string; children?: ReactNode; stroke?: boolean }) {
  return (
    <>
      <div className="page-banner" aria-hidden="true">
        <img src="/images/footer-night.jpg" alt="" />
      </div>
      <div className="page-intro">
        <h1>{title}</h1>
        {children}
      </div>
      {stroke && <BrushStroke />}
    </>
  )
}

/** A rough horizontal ink stroke between sections. */
export function BrushStroke({ flipped = false }: { flipped?: boolean }) {
  const id = `ink-${useId().replace(/:/g, "")}`
  return (
    <svg className={`brush-stroke${flipped ? " is-flipped" : ""}`} viewBox="0 0 1200 40" preserveAspectRatio="none" aria-hidden="true">
      <filter id={id} x="-2%" y="-60%" width="104%" height="220%">
        <feTurbulence type="fractalNoise" baseFrequency="0.035 0.6" numOctaves={3} seed={7}></feTurbulence>
        <feDisplacementMap in="SourceGraphic" scale={9}></feDisplacementMap>
      </filter>
      <path
        filter={`url(#${id})`}
        fill="var(--paper)"
        d="M8 22c90-9 210-13 360-12 190 1 330 6 500 3 120-2 220-6 324-4-90 7-200 13-330 16-170 4-330 1-520-1C210 22 100 26 8 22z"
      ></path>
    </svg>
  )
}

/** The katana that divides the home page sections. */
export function KatanaDivider() {
  return (
    <svg className="katana-divider" viewBox="0 0 640 44" aria-hidden="true">
      <rect x="14" y="16" width="10" height="13" fill="var(--paper)"></rect>
      <rect x="24" y="15" width="138" height="15" fill="var(--paper)"></rect>
      <g fill="var(--paper-light)">
        {[33, 47.5, 62, 76.5, 91, 105.5, 120, 134.5, 149].map((x) => (
          <path key={x} d={`M${x} 22.5l6-5 6 5-6 5z`}></path>
        ))}
      </g>
      <ellipse cx="168" cy="22.5" rx="6" ry="17" fill="var(--paper)"></ellipse>
      <rect x="175" y="16.5" width="12" height="12" fill="var(--brass)"></rect>
      <path
        d="M187 17C320 17 470 15 560 9c30-2 50-4 66-8-14 8-36 15-64 19-92 12-235 10-375 10z"
        fill="var(--paper-light)"
        stroke="var(--paper)"
        strokeWidth="1.6"
        strokeLinejoin="round"
      ></path>
      <path
        d="M190 25c20-2 30 1 46-1s26-2 44 0 30-2 48-1 26 1 46-1 34-1 52-2 30-2 50-4 40-4 62-7"
        fill="none"
        stroke="var(--mist)"
        strokeWidth="1"
      ></path>
    </svg>
  )
}
