import { useState, type ReactNode } from "react"
import { CalendarDays, Check, Plus, Trash2 } from "lucide-react"
import { SprayDisc } from "../../components/brand"
import {
  BareButton,
  Button,
  Checkbox,
  Heading,
  Input,
  Select,
  StatusBadge,
  TimeInput,
} from "../../components/ui"
import { formatMoney } from "../../lib/data"
import { limits } from "../../lib/limits"
import type {
  BookingStatus,
  Day,
  DayException,
  StaffBooking,
  WorkingHours,
} from "../../lib/types"
import {
  allDays,
  dayNames,
  formatDay,
  plural,
  shopToday,
  useNow,
} from "./staffUtils"

/** Building blocks shared by the staff and admin screens. */
export function Feedback({ error, notice }: { error?: string; notice?: string }) {
  return (
    <>
      {error ? (
        <p className="form-error" role="alert">
          {error}
        </p>
      ) : null}
      {notice ? (
        <p className="notice" role="status">
          {notice}
        </p>
      ) : null}
    </>
  )
}

export function Loading({ error }: { error?: string }) {
  return error ? <Feedback error={error} /> : <div className="spinner" />
}

export function BookingRows({
  bookings,
  update,
  busy,
  empty = "No bookings match this view.",
  extraActions,
}: {
  bookings: StaffBooking[]
  update: (booking: StaffBooking, status: BookingStatus) => void
  busy: string
  empty?: string
  /** More buttons per row (the admin's Edit / Delete) */
  extraActions?: (booking: StaffBooking) => ReactNode
}) {
  const now = useNow()
  if (!bookings.length)
    return (
      <div className="empty-state">
        <SprayDisc />
        <Heading level={3}>The chair is clear.</Heading>
        <p>{empty}</p>
      </div>
    )
  return (
    <div className="booking-table">
      {bookings.map((booking) => {
        const started = new Date(booking.startsAt).getTime() <= now
        const button = (
          status: BookingStatus,
          label: ReactNode,
          variant: "secondary" | "ghost" | "danger",
        ) => (
          <Button
            variant={variant}
            loading={busy === booking.id + status}
            disabled={!!busy}
            onClick={() => update(booking, status)}
          >
            {label}
          </Button>
        )
        return (
          <article key={booking.id}>
            <div>
              <time dateTime={booking.startsAt}>
                {formatDay(booking.date)} · {booking.time}
              </time>
              <Heading level={3}>
                {booking.anonymized || !booking.customerName
                  ? "Customer details removed"
                  : booking.customerName}
              </Heading>
              <p>
                {booking.serviceName} with {booking.artistName} ·{" "}
                {formatMoney(booking.price)} · {booking.code}
              </p>
              {!booking.anonymized ? (
                <p>
                  {[booking.phone, booking.email].filter(Boolean).join(" · ")}
                  {booking.notes ? ` · “${booking.notes}”` : ""}
                </p>
              ) : null}
            </div>
            {booking.awaitingStatus ? (
              <span className="status status-awaiting">Awaiting status</span>
            ) : (
              <StatusBadge status={booking.status} />
            )}
            <div className="row-actions">
              {booking.status === "BOOKED" && started ? (
                <>
                  {button("DONE", <><Check /> Done</>, "secondary")}
                  {button("NO_SHOW", "No-show", "ghost")}
                </>
              ) : null}
              {booking.status === "BOOKED"
                ? button("CANCELLED", "Cancel", "danger")
                : null}
              {booking.status === "DONE"
                ? button("NO_SHOW", "Change to no-show", "ghost")
                : null}
              {booking.status === "NO_SHOW"
                ? button("DONE", "Change to done", "ghost")
                : null}
              {extraActions?.(booking)}
            </div>
          </article>
        )
      })}
    </div>
  )
}

// ---- Working hours (artist's own page and the admin's artist form) ----

const emptyException: DayException = {
  date: "",
  closed: true,
  start: null,
  end: null,
  note: null,
}

export function ScheduleEditor({
  weeklyHours,
  exceptions,
  onChange,
}: {
  weeklyHours: WorkingHours[]
  exceptions: DayException[]
  onChange: (weeklyHours: WorkingHours[], exceptions: DayException[]) => void
}) {
  const [draft, setDraft] = useState<DayException>(emptyException)
  const today = shopToday()
  function patchDay(day: Day, hours: WorkingHours | null) {
    const rest = weeklyHours.filter((item) => item.day !== day)
    onChange(hours ? [...rest, hours] : rest, exceptions)
  }
  function addException() {
    if (!draft.date) return
    onChange(
      weeklyHours,
      [...exceptions.filter((item) => item.date !== draft.date), draft].sort(
        (a, b) => a.date.localeCompare(b.date),
      ),
    )
    setDraft(emptyException)
  }
  return (
    <>
      <p>
        Start times are on the full hour, so every 60-minute appointment
        lines up with your hours.
      </p>
      <div className="hours-panel">
        {allDays.map((day) => {
          const item = weeklyHours.find((hours) => hours.day === day)
          return (
            <div className="hours-row" key={day}>
              <Checkbox
                className="day-toggle"
                checked={!!item}
                onChange={(e) =>
                  patchDay(
                    day,
                    e.target.checked ? { day, start: "10:00", end: "18:00" } : null,
                  )
                }
                label={
                  <>
                    <span />
                    <strong>{dayNames[day]}</strong>
                  </>
                }
              />
              {item ? (
                <div>
                  <TimeInput
                    aria-label={`${dayNames[day]} start`}
                    step={3600}
                    value={item.start}
                    onChange={(e) =>
                      patchDay(day, { ...item, start: e.target.value })
                    }
                  />
                  <span>to</span>
                  <TimeInput
                    aria-label={`${dayNames[day]} end`}
                    value={item.end}
                    onChange={(e) => patchDay(day, { ...item, end: e.target.value })}
                  />
                </div>
              ) : (
                <em>Off</em>
              )}
            </div>
          )
        })}
      </div>
      <div className="exception-panel">
        <CalendarDays />
        <div>
          <Heading level={3}>Date exceptions</Heading>
          <p>A day off or different hours on one date, e.g. a holiday.</p>
          <div className="exception-form">
            <Input
              label="Date"
              type="date"
              min={today}
              value={draft.date}
              onChange={(e) => setDraft({ ...draft, date: e.target.value })}
            />
            <Select
              label="Availability"
              value={draft.closed ? "closed" : "custom"}
              onChange={(e) => {
                const closed = e.target.value === "closed"
                setDraft({
                  ...draft,
                  closed,
                  start: closed ? null : "10:00",
                  end: closed ? null : "17:00",
                })
              }}
            >
              <option value="closed">Day off</option>
              <option value="custom">Custom hours</option>
            </Select>
            {!draft.closed ? (
              <div className="exception-times">
                <TimeInput
                  aria-label="Exception start"
                  step={3600}
                  value={draft.start || "10:00"}
                  onChange={(e) => setDraft({ ...draft, start: e.target.value })}
                />
                <span>to</span>
                <TimeInput
                  aria-label="Exception end"
                  value={draft.end || "17:00"}
                  onChange={(e) => setDraft({ ...draft, end: e.target.value })}
                />
              </div>
            ) : null}
            <Input
              label="Note"
              maxLength={limits["exception.note"]}
              value={draft.note || ""}
              onChange={(e) => setDraft({ ...draft, note: e.target.value || null })}
            />
            <Button
              type="button"
              variant="secondary"
              disabled={!draft.date}
              onClick={addException}
            >
              <Plus /> Add
            </Button>
          </div>
          <div className="exception-list">
            {exceptions.map((item) => (
              <div key={item.date}>
                <span>
                  <strong>{formatDay(item.date)}</strong>
                  <small>
                    {item.closed ? "Day off" : `${item.start}–${item.end}`}
                    {item.note ? ` · ${item.note}` : ""}
                  </small>
                </span>
                <BareButton
                  type="button"
                  aria-label={`Remove ${item.date}`}
                  onClick={() =>
                    onChange(
                      weeklyHours,
                      exceptions.filter((other) => other.date !== item.date),
                    )
                  }
                >
                  <Trash2 />
                </BareButton>
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  )
}

/**
 * Upcoming bookings that fall outside the artist's hours (e.g. after adding a day off).
 * The hours are saved; these bookings stay until someone cancels them.
 */
export function ConflictWarning({
  conflicts,
  hint,
  artistName,
}: {
  conflicts: StaffBooking[]
  hint: string
  /** Shown in the heading when it isn't obvious whose hours these are (admin pages) */
  artistName?: string
}) {
  if (!conflicts.length) return null
  return (
    <div className="notice" role="status">
      <strong>
        {artistName ? `${artistName}: ` : ""}
        {plural(conflicts.length, "booking")} outside these hours
      </strong>
      <ul>
        {conflicts.map((booking) => (
          <li key={booking.id}>
            {formatDay(booking.date)} · {booking.time} ·{" "}
            {booking.customerName || "Customer details removed"} (
            {booking.serviceName})
          </li>
        ))}
      </ul>
      <p>{hint}</p>
    </div>
  )
}
