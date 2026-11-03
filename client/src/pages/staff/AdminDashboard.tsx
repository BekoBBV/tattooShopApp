import { useState } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { Button, Eyebrow, Heading } from "../../components/ui"
import { formatMoney } from "../../lib/data"
import type { AdminStats } from "../../lib/types"
import {
  addDays,
  formatDay,
  mondayOf,
  plural,
  shopToday,
  useStaffLoad,
} from "../../features/staff/staffUtils"
import { BookingsBrowser } from "../../features/staff/BookingsBrowser"
import { Loading } from "../../features/staff/StaffUi"

/** Admin: weekly overview with stats (/admin) and every booking with filters (/admin/bookings). */
export function AdminOverview() {
  const thisWeek = mondayOf(shopToday())
  const [week, setWeek] = useState(thisWeek)
  const { data: stats, error } = useStaffLoad<AdminStats>(
    `/admin/stats?from=${week}&to=${addDays(week, 6)}`,
  )
  const label = week === thisWeek ? "This week" : `Week of ${formatDay(week)}`
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>
            {label} · {formatDay(week)} – {formatDay(addDays(week, 6))}
          </Eyebrow>
          <Heading level={1}>Shop overview.</Heading>
        </div>
        <div className="row-actions">
          <Button
            variant="ghost"
            aria-label="Previous week"
            onClick={() => setWeek(addDays(week, -7))}
          >
            <ChevronLeft />
          </Button>
          <Button
            variant="secondary"
            disabled={week === thisWeek}
            onClick={() => setWeek(thisWeek)}
          >
            This week
          </Button>
          <Button
            variant="ghost"
            aria-label="Next week"
            onClick={() => setWeek(addDays(week, 7))}
          >
            <ChevronRight />
          </Button>
        </div>
      </div>
      {!stats || error ? (
        <Loading error={error} />
      ) : (
        <>
          <div className="stat-grid">
            <div>
              <span>Bookings</span>
              <strong>{stats.bookings}</strong>
              <small>{stats.cancelled} cancelled</small>
            </div>
            <div>
              <span>Revenue</span>
              <strong>{formatMoney(stats.revenue)}</strong>
              <small>{plural(stats.done, "done booking")}</small>
            </div>
            <div>
              <span>No-shows</span>
              <strong>{stats.noShows}</strong>
              <small>
                {stats.busiestArtist
                  ? `Busiest: ${stats.busiestArtist.name} (${stats.busiestArtist.bookings})`
                  : "No bookings yet"}
              </small>
            </div>
          </div>
          <Heading level={2}>Per artist</Heading>
          <div className="booking-table">
            {stats.perArtist.map((row) => (
              <article key={row.artistId}>
                <div>
                  <Heading level={3}>{row.name}</Heading>
                  <p>
                    {plural(row.bookings, "booking")} · {row.done} done ·{" "}
                    {plural(row.noShows, "no-show")}
                  </p>
                </div>
                <strong>{formatMoney(row.revenue)}</strong>
              </article>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

export function AdminBookings() {
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Full rotation</Eyebrow>
          <Heading level={1}>All bookings.</Heading>
        </div>
      </div>
      <BookingsBrowser admin />
    </div>
  )
}
