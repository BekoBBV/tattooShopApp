import { createBrowserRouter } from "react-router"
import Home from "../pages/Home"
import { StaffLogin } from "../pages/staff/StaffLogin"
import { AdminGuard, ArtistGuard } from "../pages/staff/StaffLayout"
import { ArtistBookings, ArtistDashboard, StaffHours } from "../pages/staff/ArtistPages"
import { AdminBookings, AdminOverview } from "../pages/staff/AdminDashboard"
import { AdminServices } from "../pages/staff/AdminServices"
import { AdminArtists } from "../pages/staff/AdminArtists"
import { AdminMessages, AdminReviews } from "../pages/staff/AdminInbox"
import { AdminUsers } from "../pages/staff/AdminUsers"
import {
  ArtistStep,
  Confirmation,
  DetailsStep,
  ServiceStep,
  TimeStep,
} from "../pages/Booking"
import { CancellationLookup } from "../pages/Manage"
import { AccountRegister, AccountSignIn, MyBookings } from "../pages/Account"
import { NotFound } from "../pages/NotFound"

export const router = createBrowserRouter([
  { path: "/", Component: Home },
  { path: "/book/service", Component: ServiceStep },
  { path: "/book/artist", Component: ArtistStep },
  { path: "/book/time", Component: TimeStep },
  { path: "/book/details", Component: DetailsStep },
  { path: "/booking/confirmed", Component: Confirmation },
  { path: "/cancel", Component: CancellationLookup },
  { path: "/account", Component: MyBookings },
  { path: "/account/sign-in", Component: AccountSignIn },
  { path: "/account/register", Component: AccountRegister },
  { path: "/staff/login", Component: StaffLogin },
  {
    path: "/staff",
    Component: ArtistGuard,
    children: [
      { index: true, Component: ArtistDashboard },
      { path: "bookings", Component: ArtistBookings },
      { path: "hours", Component: StaffHours },
    ],
  },
  {
    path: "/admin",
    Component: AdminGuard,
    children: [
      { index: true, Component: AdminOverview },
      { path: "bookings", Component: AdminBookings },
      { path: "services", Component: AdminServices },
      { path: "artists", Component: AdminArtists },
      { path: "reviews", Component: AdminReviews },
      { path: "messages", Component: AdminMessages },
      { path: "users", Component: AdminUsers },
    ],
  },
  { path: "*", Component: NotFound },
])
