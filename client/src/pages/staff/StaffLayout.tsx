import { useEffect, useState, type ReactNode } from "react"
import {
  CalendarDays,
  Clock3,
  KeyRound,
  LogOut,
  Menu,
  MessageSquare,
  Scissors,
  Settings2,
  Star,
  Users,
  X,
} from "lucide-react"
import {
  Navigate,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router"
import { SprayDisc } from "../../components/brand"
import { BareButton, Button } from "../../components/ui"
import {
  SESSION_EXPIRED_EVENT,
  clearSession,
  getSession,
  isSessionExpired,
} from "../../lib/api"
import type { AuthUser, LoginResponse } from "../../lib/types"
import { formatTime, initials } from "../../features/staff/staffUtils"
import { usePageTitle } from "../../lib/usePageTitle"

/** Sign-in guards and the dashboard frame (sidebar + top bar) around every staff page. */
function Guard({ role }: { role: AuthUser["role"] }) {
  const session = getSession()
  if (!session || isSessionExpired(session)) {
    clearSession()
    return <Navigate to="/staff/login" replace state={{ expired: !!session }} />
  }
  if (session.user.role !== role)
    return (
      <Navigate
        to={session.user.role === "ADMIN" ? "/admin" : "/staff"}
        replace
      />
    )
  return (
    <DashboardShell session={session}>
      <Outlet />
    </DashboardShell>
  )
}
export function ArtistGuard() {
  return <Guard role="ARTIST" />
}
export function AdminGuard() {
  return <Guard role="ADMIN" />
}
function DashboardShell({
  session,
  children,
}: {
  session: LoginResponse
  children: ReactNode
}) {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const user = session.user
  const admin = user.role === "ADMIN"
  // Any request answered with 401 (token expired or revoked) sends the user back to sign in
  useEffect(() => {
    const onExpired = () =>
      navigate("/staff/login", { replace: true, state: { expired: true } })
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired)
  }, [navigate])
  const links = admin
    ? ([
        ["/admin", "Overview", CalendarDays],
        ["/admin/bookings", "All bookings", Users],
        ["/admin/services", "Services", Settings2],
        ["/admin/artists", "Artists", Scissors],
        ["/admin/reviews", "Reviews", Star],
        ["/admin/messages", "Messages", MessageSquare],
        ["/admin/users", "Users & rights", KeyRound],
      ] as const)
    : ([
        ["/staff", "Today / upcoming", CalendarDays],
        ["/staff/bookings", "All my bookings", Users],
        ["/staff/hours", "My hours", Clock3],
      ] as const)
  const { pathname } = useLocation()
  usePageTitle(links.find(([to]) => to === pathname)?.[1] ?? (admin ? "Admin" : "Staff"))
  return (
    <main className="dashboard">
      <aside className={`dashboard-sidebar ${open ? "open" : ""}`}>
        <div className="brand">
          <Scissors /> TATTOO <span>STUDIO</span>
        </div>
        <BareButton
          className="sidebar-close"
          onClick={() => setOpen(false)}
          aria-label="Close menu"
        >
          <X />
        </BareButton>
        <div className="staff-profile">
          <SprayDisc initials={initials(user.username)} size="sm" />
          <span>
            <small>Signed in as</small>
            <strong>{user.username}</strong>
          </span>
        </div>
        <nav>
          {links.map(([to, label, Icon]) => (
            <NavLink
              end={to === "/admin" || to === "/staff"}
              to={to}
              key={to}
              onClick={() => setOpen(false)}
            >
              <Icon /> {label}
            </NavLink>
          ))}
        </nav>
        <Button
          variant="ghost"
          onClick={() => {
            clearSession()
            navigate("/staff/login", { replace: true })
          }}
        >
          <LogOut /> Sign out
        </Button>
      </aside>
      <section className="dashboard-main">
        <header className="dashboard-top">
          <Button
            variant="ghost"
            className="dashboard-menu"
            onClick={() => setOpen(true)}
            aria-label="Open menu"
          >
            <Menu />
          </Button>
          <span>Tattoo Studio / {admin ? "Admin" : "Staff"} deck</span>
          <span className="live-dot">
            Signed in until {formatTime(session.expiresAt)}
          </span>
        </header>
        {children}
      </section>
    </main>
  )
}
