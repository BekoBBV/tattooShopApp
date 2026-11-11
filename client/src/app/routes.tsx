import type { ComponentType } from "react"
import { createBrowserRouter, Navigate, type RouteObject } from "react-router"
import { BookingLayout, RootLayout, SiteLayout } from "../components/Layout"
import Account from "../pages/Account"
import Aftercare from "../pages/Aftercare"
import ArtistPage from "../pages/ArtistPage"
import Artists from "../pages/Artists"
import { BookChoose, BookDeposit, BookDetails, BookTime } from "../pages/Booking"
import BookingConfirmed from "../pages/BookingConfirmed"
import Credits from "../pages/Credits"
import FlashGallery from "../pages/FlashGallery"
import Home from "../pages/Home"
import Manage from "../pages/Manage"
import NotFound from "../pages/NotFound"
import { Register, SignIn } from "../pages/SignIn"

/** A page that is only downloaded when someone opens it (the staff area, which customers never see). */
const page = (load: () => Promise<{ default: ComponentType }>) => async () => ({ Component: (await load()).default })

/** Every page of the website: the customer site, the booking steps, and the artist and admin area. */
export const routes: RouteObject[] = [
  {
    Component: RootLayout,
    children: [
      // A page of its own, without the site's header and footer (as in the design)
      { path: "/staff/sign-in", lazy: page(() => import("../pages/StaffSignIn")) },
      {
        // The artist area (/staff) and the admin area (/admin); StaffLayout signs people in and keeps each in their own
        lazy: async () => ({ Component: (await import("../components/StaffUi")).StaffLayout }),
        children: [
          { path: "/staff", element: <Navigate to="/staff/calendar" replace /> },
          { path: "/staff/calendar", lazy: page(() => import("../pages/staff/StaffCalendar")) },
          { path: "/staff/hours", lazy: page(() => import("../pages/staff/StaffHours")) },
          { path: "/staff/flash", lazy: page(() => import("../pages/staff/StaffFlash")) },
          { path: "/staff/*", element: <Navigate to="/staff/calendar" replace /> },
          { path: "/admin", element: <Navigate to="/admin/bookings" replace /> },
          { path: "/admin/bookings", lazy: page(() => import("../pages/admin/AdminBookings")) },
          { path: "/admin/flash", lazy: page(() => import("../pages/admin/AdminFlash")) },
          { path: "/admin/artists", lazy: page(() => import("../pages/admin/AdminArtists")) },
          { path: "/admin/reviews", lazy: page(() => import("../pages/admin/AdminReviews")) },
          { path: "/admin/messages", lazy: page(() => import("../pages/admin/AdminMessages")) },
          { path: "/admin/users", lazy: page(() => import("../pages/admin/AdminUsers")) },
          { path: "/admin/*", element: <Navigate to="/admin/bookings" replace /> },
        ],
      },
      {
        Component: SiteLayout,
        children: [
          { path: "/", Component: Home },
          { path: "/flash", Component: FlashGallery },
          { path: "/artists", Component: Artists },
          { path: "/artists/:slug", Component: ArtistPage },
          { path: "/aftercare", Component: Aftercare },
          { path: "/credits", Component: Credits },
          { path: "/booking/confirmed", Component: BookingConfirmed },
          { path: "/manage", Component: Manage },
          { path: "/account", Component: Account },
          { path: "/account/sign-in", Component: SignIn },
          { path: "/account/register", Component: Register },
          { path: "*", Component: NotFound },
        ],
      },
      {
        Component: BookingLayout,
        children: [
          { path: "/book", Component: BookChoose },
          { path: "/book/choose", Component: BookChoose },
          { path: "/book/time", Component: BookTime },
          { path: "/book/details", Component: BookDetails },
          { path: "/book/deposit", Component: BookDeposit },
        ],
      },
    ],
  },
]

/** The website's router; the page tests build their own from `routes`, starting at any address. */
export const router = createBrowserRouter(routes)
