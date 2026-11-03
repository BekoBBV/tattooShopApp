import { useState } from "react"
import { Settings2 } from "lucide-react"
import { AppLink, Button, Eyebrow, Heading } from "../../components/ui"
import { api } from "../../lib/api"
import type { DayException, StaffBooking, WorkingHours } from "../../lib/types"
import {
  changeBookingStatus,
  confirmStatusChange,
  token,
  useAction,
  useBookings,
  useNow,
  useStaffLoad,
} from "../../features/staff/staffUtils"
import {
  BookingRows,
  ConflictWarning,
  Feedback,
  Loading,
  ScheduleEditor,
} from "../../features/staff/StaffUi"
import { BookingsBrowser } from "../../features/staff/BookingsBrowser"

/** The artist's own pages: today / upcoming bookings (/staff) and working hours (/staff/hours). */
export function ArtistDashboard() {
  // The overview: everything from the last week to 30 days ahead (the default range) in one page.
  // 500 covers it even for a full chair every day (37 days × at most 11 one-hour slots)
  const data = useBookings("/staff/bookings?size=500", false)
  const now = useNow()
  const awaiting = data.bookings.filter((b) => b.awaitingStatus)
  // Still to come (including cancelled ones, so the artist sees what changed) or in progress
  const upcoming = data.bookings.filter(
    (b) =>
      !b.awaitingStatus &&
      (b.status === "BOOKED" || new Date(b.startsAt).getTime() > now),
  )
  const recent = data.bookings
    .filter(
      (b) => b.status !== "BOOKED" && new Date(b.startsAt).getTime() <= now,
    )
    .reverse()
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Your chair</Eyebrow>
          <Heading level={1}>Today / upcoming.</Heading>
        </div>
        <AppLink to="/staff/hours" variant="secondary">
          <Settings2 /> My hours
        </AppLink>
      </div>
      <Feedback error={data.action.error} />
      {data.loading && !data.data ? (
        <Loading error={data.error} />
      ) : (
        <>
          <Feedback error={data.error} />
          {awaiting.length ? (
            <>
              <Heading level={2}>Awaiting status</Heading>
              <p>These appointments are over. Mark each one done or no-show.</p>
              <BookingRows
                bookings={awaiting}
                update={data.update}
                busy={data.action.busy}
              />
            </>
          ) : null}
          <Heading level={2}>Up next</Heading>
          <BookingRows
            bookings={upcoming}
            update={data.update}
            busy={data.action.busy}
            empty="No upcoming bookings in the next 30 days."
          />
          <Heading level={2}>Last 7 days</Heading>
          <BookingRows
            bookings={recent}
            update={data.update}
            busy={data.action.busy}
            empty="Nothing finished in the last week."
          />
        </>
      )}
    </div>
  )
}

/** /staff/bookings: all the artist's bookings with search, filters, sorting and pages. */
export function ArtistBookings() {
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Your chair</Eyebrow>
          <Heading level={1}>All my bookings.</Heading>
        </div>
      </div>
      <BookingsBrowser admin={false} />
    </div>
  )
}

type Schedule = {
  weeklyHours: WorkingHours[]
  exceptions: DayException[]
  /** Upcoming bookings outside these hours (from the server) */
  conflicts: StaffBooking[]
}
export function StaffHours() {
  const load = useStaffLoad<Schedule>("/staff/schedule")
  // The artist's unsaved edits; until the first edit, the loaded schedule is shown
  const [edited, setSchedule] = useState<Schedule | null>(null)
  const schedule = edited ?? load.data
  const action = useAction()
  const cancel = useAction()
  async function save() {
    if (!schedule) return
    await action.run(
      "save",
      async () =>
        setSchedule(
          await api<Schedule>("/staff/schedule", {
            method: "PUT",
            token: token(),
            body: JSON.stringify({
              weeklyHours: schedule.weeklyHours,
              exceptions: schedule.exceptions,
            }),
          }),
        ),
      "Hours saved. New bookings follow them right away; existing bookings stay as they are.",
    )
  }
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Set the rhythm</Eyebrow>
          <Heading level={1}>My hours.</Heading>
        </div>
        <Button loading={action.busy === "save"} disabled={!schedule} onClick={save}>
          Save changes
        </Button>
      </div>
      <Feedback error={action.error || cancel.error} notice={action.notice} />
      {schedule?.conflicts.length ? (
        <>
          <ConflictWarning
            conflicts={schedule.conflicts}
            hint="They are still booked. Keep them, or cancel them here (the customer gets an email if they gave one)."
          />
          <BookingRows
            bookings={schedule.conflicts}
            busy={cancel.busy}
            update={(booking, status) => {
              if (!confirmStatusChange(booking, status)) return
              cancel
                .run(booking.id + status, async () => {
                  await changeBookingStatus(booking, status, false)
                  // Refresh only the warning, so unsaved changes to the hours are kept
                  const fresh = await api<Schedule>("/staff/schedule", { token: token() })
                  setSchedule((current) => ({
                    ...(current ?? fresh),
                    conflicts: fresh.conflicts,
                  }))
                })
            }}
          />
        </>
      ) : null}
      {schedule ? (
        <ScheduleEditor
          weeklyHours={schedule.weeklyHours}
          exceptions={schedule.exceptions}
          onChange={(weeklyHours, exceptions) => {
            action.clear()
            setSchedule({ ...schedule, weeklyHours, exceptions })
          }}
        />
      ) : (
        <Loading error={load.error} />
      )}
    </div>
  )
}
