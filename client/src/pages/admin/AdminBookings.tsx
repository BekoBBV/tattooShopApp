import { useEffect, useState } from "react"
import { StaffBookingPanel } from "../../components/StaffBookingPanel"
import { ConfirmDialog, NightStamp, Pager, StaffHeader, StaffState, StatusLine } from "../../components/StaffUi"
import { euros } from "../../lib/format"
import { limits } from "../../lib/limits"
import { json, staffApi, useStaff } from "../../lib/staff"
import { canCancel, depositStamp, statusLabel, tableDate, typeLabel } from "../../lib/staffText"
import type { ArtistOption, Page, StaffBooking, Stats } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

const DEPOSITS: Record<string, string> = {
  "": "",
  online: "&depositStatus=PAID&depositMethod=ONLINE",
  cash: "&depositStatus=PAID&depositMethod=CASH",
  waiting: "&depositStatus=WAITING",
  refunded: "&depositStatus=REFUNDED",
  kept: "&depositStatus=KEPT",
  none: "&depositStatus=NONE",
}

/** Bookings: every artist's appointments with their deposits, and what the studio can do with them. */
export default function AdminBookings() {
  usePageTitle("Bookings")
  const staff = useStaff()
  const token = staff?.token
  const [filters, setFilters] = useState({ date: "", artistId: "", type: "", status: "", deposit: "" })
  const [typed, setTyped] = useState("")
  const [q, setQ] = useState("")
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<StaffBooking | null>(null)
  const [cancelling, setCancelling] = useState<StaffBooking | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")

  useEffect(() => {
    const timer = window.setTimeout(() => setQ(typed.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [typed])

  const query =
    `/admin/bookings?page=${page}&size=25&sort=date,asc` +
    (filters.date ? `&from=${filters.date}&to=${filters.date}` : "") +
    (filters.artistId ? `&artistId=${filters.artistId}` : "") +
    (filters.type ? `&type=${filters.type}` : "") +
    (filters.status ? `&status=${filters.status}` : "") +
    DEPOSITS[filters.deposit] +
    (q ? `&q=${encodeURIComponent(q)}` : "")
  const bookings = useLoad<Page<StaffBooking>>(query, token)
  const artists = useLoad<ArtistOption[]>("/admin/artists/options", token)
  const stats = useLoad<Stats>("/admin/stats", token)

  const setFilter = (field: keyof typeof filters) => (e: { target: { value: string } }) => {
    setFilters({ ...filters, [field]: e.target.value })
    setPage(0)
  }
  const say = (text: string) => {
    setNote(text)
    setProblem("")
  }
  const changed = (text: string) => {
    say(text)
    bookings.reload()
    stats.reload()
  }

  async function act(path: string, init: RequestInit, text: string) {
    try {
      await staffApi(path, init)
      changed(text)
    } catch (reason) {
      setProblem(errorText(reason))
    }
  }

  const s = stats.data
  const items = bookings.data?.items ?? []
  return (
    <>
      <StaffHeader title="Bookings">All appointments and deposit states.</StaffHeader>
      {s && (
        <p className="stats-line">
          This week: <strong>{s.bookings}</strong> booked, <strong>{s.done}</strong> done, <strong>{s.noShows}</strong> no-show
          {s.noShows === 1 ? "" : "s"}, <strong>{s.cancelled}</strong> cancelled, <strong>{euros(s.revenue)}</strong> taken
          {s.busiestArtist ? `. Busiest: ${s.busiestArtist.name} (${s.busiestArtist.bookings})` : ""}.
        </p>
      )}
      <StatusLine>{note}</StatusLine>
      <StatusLine error>{problem}</StatusLine>
      <div className="admin-filters">
        <label>
          Date
          <input type="date" value={filters.date} onChange={setFilter("date")} />
        </label>
        <label>
          Artist
          <select value={filters.artistId} onChange={setFilter("artistId")}>
            <option value="">All</option>
            {artists.data?.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
                {a.active ? "" : " (inactive)"}
              </option>
            ))}
          </select>
        </label>
        <label>
          Type
          <select value={filters.type} onChange={setFilter("type")}>
            <option value="">All</option>
            <option value="FLASH">Flash</option>
            <option value="CONSULTATION">Consultation</option>
            <option value="SESSION">Reserved session</option>
          </select>
        </label>
        <label>
          Status
          <select value={filters.status} onChange={setFilter("status")}>
            <option value="">All</option>
            {Object.entries(statusLabel).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
        <label>
          Deposit status
          <select value={filters.deposit} onChange={setFilter("deposit")}>
            <option value="">All</option>
            <option value="online">Paid with Revolut</option>
            <option value="cash">Paid in cash</option>
            <option value="waiting">Waiting</option>
            <option value="refunded">Refunded</option>
            <option value="kept">Kept</option>
            <option value="none">No deposit</option>
          </select>
        </label>
        <label>
          Search
          <input type="search" value={typed} maxLength={limits["search.q"]} placeholder="Name, phone, code" onChange={(e) => {
            setTyped(e.target.value)
            setPage(0)
          }} />
        </label>
      </div>
      {!filters.date && <p className="staff-note">Showing the last 7 days and the next 30. Pick a date to see any other day.</p>}
      <StaffState loading={bookings.loading && !bookings.data} error={bookings.error} onRetry={bookings.reload}
        empty={bookings.data && items.length === 0 && "No bookings match."} />
      {items.length > 0 && (
        <div className="table-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Time</th>
                <th>Customer</th>
                <th>Artist</th>
                <th>Type</th>
                <th>Status</th>
                <th>Deposit</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((b) => {
                const deposit = depositStamp(b)
                return (
                  <tr key={b.id} className={b.depositOverdue || b.awaitingStatus ? "is-expired" : ""}>
                    <td>{tableDate(b)}</td>
                    <td>{b.time}</td>
                    <td>
                      <button className="table-link" type="button" onClick={() => setSelected(b)}>
                        {b.anonymized ? "Removed for privacy" : b.customerName}
                      </button>
                    </td>
                    <td>{b.artistName}</td>
                    <td>{typeLabel[b.type]}</td>
                    <td>
                      {statusLabel[b.status]}
                      {b.awaitingStatus && <small>Mark Done or No-show</small>}
                    </td>
                    <td>
                      <NightStamp tone={deposit.tone}>{deposit.label}</NightStamp>
                      {deposit.note && <small>{deposit.note}</small>}
                    </td>
                    <td>
                      <div className="table-actions">
                        {b.deposit.status === "WAITING" && canCancel(b) && (
                          <button className="staff-text-button" type="button"
                            onClick={() => act(`/admin/bookings/${b.id}/deposit-cash`, { method: "POST" }, `Deposit for ${b.customerName} marked as paid in cash.`)}>
                            Mark deposit paid in cash
                          </button>
                        )}
                        {b.awaitingStatus && (
                          <>
                            <button className="staff-text-button" type="button"
                              onClick={() => act(`/admin/bookings/${b.id}`, json("PATCH", { status: "DONE" }), `${b.customerName}'s appointment marked as done.`)}>
                              Done
                            </button>
                            <button className="staff-text-button" type="button"
                              onClick={() => act(`/admin/bookings/${b.id}`, json("PATCH", { status: "NO_SHOW" }), `${b.customerName} marked as a no-show.`)}>
                              No-show
                            </button>
                          </>
                        )}
                        {canCancel(b) && (
                          <button className="staff-text-button" type="button" onClick={() => setCancelling(b)}>
                            Cancel booking
                          </button>
                        )}
                        <button className="staff-text-button" type="button" onClick={() => setSelected(b)}>
                          Details
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {bookings.data && <Pager page={bookings.data.page} totalPages={bookings.data.totalPages} onPage={setPage} />}

      {selected && (
        <StaffBookingPanel
          booking={selected}
          admin
          onClose={() => setSelected(null)}
          onChanged={(updated, text) => {
            setSelected(updated)
            changed(text)
          }}
        />
      )}
      {cancelling && (
        <ConfirmDialog
          title="Cancel this booking?"
          confirmLabel="Cancel booking"
          keepLabel="Keep booking"
          onClose={() => setCancelling(null)}
          onConfirm={async () => {
            await staffApi(`/admin/bookings/${cancelling.id}`, json("PATCH", { status: "CANCELLED" }))
            const who = cancelling.customerName ?? "The customer"
            setCancelling(null)
            changed(`Booking cancelled. ${who} gets an email${cancelling.deposit.status === "PAID" ? " and the deposit is refunded" : ""}.`)
          }}
        >
          {cancelling.customerName ?? "The customer"} gets an email that the studio cancelled.
          {cancelling.deposit.status === "PAID" ? ` The ${euros(cancelling.deposit.amount)} deposit is refunded.` : ""}
        </ConfirmDialog>
      )}
    </>
  )
}
