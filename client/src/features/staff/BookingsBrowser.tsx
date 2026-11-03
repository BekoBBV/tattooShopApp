import { useState, type FormEvent } from "react"
import { Button, Heading, Input, Select, Textarea } from "../../components/ui"
import { ListToolbar, Pager } from "../../components/ListControls"
import { useListQuery } from "../../lib/paging"
import type { ArtistOption, StaffBooking } from "../../lib/types"
import { addDays, send, shopToday, useAction, useBookings, useStaffLoad } from "./staffUtils"
import { BookingRows, Feedback, Loading } from "./StaffUi"
import { limits } from "../../lib/limits"

const bookingSorts: [string, string][] = [
  ["date,asc", "Date: earliest first"],
  ["date,desc", "Date: latest first"],
  ["customer,asc", "Customer A–Z"],
  ["service,asc", "Service A–Z"],
  ["price,desc", "Price: high to low"],
  ["status,asc", "Status"],
]

/**
 * Searchable, sortable, paged bookings with date range and status filters: every booking for
 * the admin (with an artist filter), the artist's own bookings otherwise.
 */
export function BookingsBrowser({ admin }: { admin: boolean }) {
  const today = shopToday()
  const list = useListQuery({
    sort: "date,asc",
    filters: { from: addDays(today, -7), to: addDays(today, 30), status: "", artistId: "" },
  })
  const data = useBookings(`${admin ? "/admin" : "/staff"}/bookings?${list.query}`, admin)
  const artists = useStaffLoad<ArtistOption[]>(admin ? "/admin/artists/options" : null)
  // The admin corrects a booking's customer details, or deletes it
  const [editing, setEditing] = useState<StaffBooking | null>(null)
  const [draft, setDraft] = useState({ customerName: "", phone: "", email: "", notes: "" })
  const edit = useAction()
  async function saveEdit(event: FormEvent) {
    event.preventDefault()
    if (!editing) return
    const ok = await edit.run(
      "save",
      () => send(`/admin/bookings/${editing.id}`, "PUT", draft),
      `Booking ${editing.code} saved.`,
    )
    if (ok) {
      setEditing(null)
      data.reload()
    }
  }
  function remove(booking: StaffBooking) {
    if (
      !window.confirm(
        `Delete booking ${booking.code} for good? The customer is not told; to let them know, cancel it instead.`,
      )
    )
      return
    edit.run(booking.id, () => send(`/admin/bookings/${booking.id}`, "DELETE"), `Booking ${booking.code} deleted.`)
      .then((ok) => ok && data.reload())
  }
  const adminActions = (booking: StaffBooking) => (
    <>
      {booking.anonymized ? null : (
        <Button
          variant="ghost"
          onClick={() => {
            setEditing(booking)
            setDraft({
              customerName: booking.customerName ?? "",
              phone: booking.phone ?? "",
              email: booking.email ?? "",
              notes: booking.notes ?? "",
            })
            edit.clear()
          }}
        >
          Edit
        </Button>
      )}
      <Button variant="danger" disabled={edit.busy === booking.id} onClick={() => remove(booking)}>
        Delete
      </Button>
    </>
  )
  return (
    <>
      <ListToolbar
        list={list}
        searchLabel="Search bookings"
        placeholder="Name, phone, email, code…"
        sorts={bookingSorts}
      >
        {admin ? (
          <Select
            label="Artist"
            value={list.filters.artistId}
            onChange={(e) => list.setFilter("artistId", e.target.value)}
          >
            <option value="">All</option>
            {(artists.data || []).map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
                {item.active ? "" : " (inactive)"}
              </option>
            ))}
          </Select>
        ) : null}
        <Select
          label="Status"
          value={list.filters.status}
          onChange={(e) => list.setFilter("status", e.target.value)}
        >
          <option value="">All</option>
          <option value="BOOKED">Confirmed</option>
          <option value="DONE">Completed</option>
          <option value="NO_SHOW">No-show</option>
          <option value="CANCELLED">Cancelled</option>
        </Select>
        <Input
          label="From"
          type="date"
          value={list.filters.from}
          onChange={(e) => list.setFilter("from", e.target.value)}
        />
        <Input
          label="To"
          type="date"
          value={list.filters.to}
          onChange={(e) => list.setFilter("to", e.target.value)}
        />
      </ListToolbar>
      {editing ? (
        <form className="details-form admin-form" onSubmit={saveEdit}>
          <Heading level={3}>Edit booking {editing.code}</Heading>
          <p>
            Time, artist and service stay as booked. To move the appointment, cancel it and book a new time.
          </p>
          <Input
            label="Customer name"
            required
            maxLength={limits["booking.customerName"]}
            value={draft.customerName}
            error={edit.fieldErrors.customerName}
            onChange={(e) => setDraft({ ...draft, customerName: e.target.value })}
          />
          <Input
            label="Phone"
            type="tel"
            required
            maxLength={limits["booking.phone"]}
            value={draft.phone}
            error={edit.fieldErrors.phone}
            onChange={(e) => setDraft({ ...draft, phone: e.target.value })}
          />
          <Input
            label="Email (optional)"
            type="email"
            maxLength={limits["booking.email"]}
            value={draft.email}
            error={edit.fieldErrors.email}
            onChange={(e) => setDraft({ ...draft, email: e.target.value })}
          />
          <Textarea
            label="Notes"
            maxLength={limits["booking.notes"]}
            rows={3}
            value={draft.notes}
            error={edit.fieldErrors.notes}
            onChange={(e) => setDraft({ ...draft, notes: e.target.value })}
          />
          <Feedback error={edit.error} />
          <div className="row-actions">
            <Button type="submit" loading={edit.busy === "save"}>
              Save booking
            </Button>
            <Button type="button" variant="ghost" onClick={() => setEditing(null)}>
              Cancel edit
            </Button>
          </div>
        </form>
      ) : null}
      <Feedback error={data.action.error || data.error || (editing ? "" : edit.error)} notice={editing ? "" : edit.notice} />
      {data.loading && !data.data ? (
        <Loading />
      ) : (
        <BookingRows
          bookings={data.bookings}
          update={data.update}
          busy={data.action.busy}
          extraActions={admin ? adminActions : undefined}
        />
      )}
      <Pager data={data.data} list={list} noun="booking" />
    </>
  )
}
