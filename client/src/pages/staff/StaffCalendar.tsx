import { ChevronLeft, ChevronRight, Plus, Search } from "lucide-react"
import { useEffect, useState, type CSSProperties, type FormEvent } from "react"
import { StaffBookingPanel } from "../../components/StaffBookingPanel"
import { SidePanel, StaffHeader, StaffState, StatusLine } from "../../components/StaffUi"
import { FormError, SelectField, TextField } from "../../components/Studio"
import { ApiError } from "../../lib/api"
import { longDate, todayInSofia } from "../../lib/format"
import { limits } from "../../lib/limits"
import { addDays, halfHours, json, mondayOf, staffApi, useStaff, weekdayOf } from "../../lib/staff"
import { bookingTitle, DAY_NAMES, hoursSummary } from "../../lib/staffText"
import type { CustomerOption, Page, Schedule, StaffBooking } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

/** A day's hours: special hours or a day off on that date, otherwise the weekly hours. */
function hoursOn(schedule: Schedule | null, date: string) {
  if (!schedule) return { open: true, text: "", off: false, note: null as string | null }
  const exception = schedule.exceptions.find((e) => e.date === date)
  const weekly = schedule.weeklyHours.find((h) => h.day === weekdayOf(date))
  if (exception?.closed) return { open: false, off: true, text: weekly ? `${weekly.start} to ${weekly.end}` : "", note: exception.note }
  if (exception) return { open: true, off: false, text: `${exception.start} to ${exception.end}`, note: exception.note }
  if (weekly) return { open: true, off: false, text: `${weekly.start} to ${weekly.end}`, note: null }
  return { open: false, off: false, text: "Closed", note: null }
}

/** "10 to 15 November 2026", "28 November to 3 December 2026" */
function rangeLabel(first: string, last: string) {
  const [fy, fm] = first.split("-")
  const [ly, lm] = last.split("-")
  const day = (d: string) => String(Number(d.slice(8)))
  const month = (d: string) => new Intl.DateTimeFormat("en-GB", { month: "long", timeZone: "UTC" }).format(new Date(`${d}T12:00:00Z`))
  if (first === last) return longDate(first)
  if (fy === ly && fm === lm) return `${day(first)} to ${day(last)} ${month(last)} ${ly}`
  if (fy === ly) return `${day(first)} ${month(first)} to ${day(last)} ${month(last)} ${ly}`
  return `${day(first)} ${month(first)} ${fy} to ${day(last)} ${month(last)} ${ly}`
}

const blockClass = { FLASH: "block-flash", CONSULTATION: "block-consultation", SESSION: "block-reserved" } as const

/** The artist's own calendar: a week (or a day) of appointments, the details panel and Reserve time. */
export default function StaffCalendar() {
  usePageTitle("My calendar")
  const staff = useStaff()
  const token = staff?.token
  const today = todayInSofia()
  const [view, setView] = useState<"week" | "day">("week")
  const [anchor, setAnchor] = useState(today)
  const [selected, setSelected] = useState<StaffBooking | null>(null)
  const [reserving, setReserving] = useState<null | { customer: CustomerOption | null; date: string }>(null)
  const [note, setNote] = useState("")

  const from = view === "week" ? mondayOf(anchor) : anchor
  const to = view === "week" ? addDays(from, 6) : anchor
  const schedule = useLoad<Schedule>("/staff/schedule", token)
  const bookings = useLoad<Page<StaffBooking>>(`/staff/bookings?from=${from}&to=${to}&size=200&sort=date,asc`, token)

  const all = (bookings.data?.items ?? []).filter((b) => b.status !== "CANCELLED" && b.status !== "EXPIRED")
  const dates = view === "day" ? [anchor] : Array.from({ length: 7 }, (_, i) => addDays(from, i))
  // A week shows the days the artist works, plus any day that has bookings anyway
  const shown = dates.filter((date) => {
    const hours = hoursOn(schedule.data, date)
    return view === "day" || hours.open || hours.off || all.some((b) => b.date === date)
  })

  const step = (direction: number) => setAnchor(addDays(anchor, direction * (view === "week" ? 7 : 1)))

  return (
    <>
      <StaffHeader
        title="My calendar"
        action={
          <button className="button button-primary staff-compact" type="button" onClick={() => setReserving({ customer: null, date: anchor < today ? today : anchor })}>
            <Plus className="icon" size={18} aria-hidden="true" /> Reserve time
          </button>
        }
      >
        {schedule.data ? hoursSummary(schedule.data.weeklyHours) : " "}
      </StaffHeader>
      <StatusLine>{note}</StatusLine>
      <div className="calendar-toolbar">
        <div className="segmented" role="group" aria-label="View">
          {(["day", "week"] as const).map((v) => (
            <button key={v} type="button" className={view === v ? "is-active" : ""} aria-pressed={view === v} onClick={() => setView(v)}>
              {v === "day" ? "Day" : "Week"}
            </button>
          ))}
        </div>
        <div className="date-control">
          <button className="icon-square" type="button" aria-label={view === "week" ? "Previous week" : "Previous day"} onClick={() => step(-1)}>
            <ChevronLeft className="icon" size={20} aria-hidden="true" />
          </button>
          <strong aria-live="polite">{shown.length ? rangeLabel(shown[0], shown[shown.length - 1]) : rangeLabel(from, to)}</strong>
          <button className="icon-square" type="button" aria-label={view === "week" ? "Next week" : "Next day"} onClick={() => step(1)}>
            <ChevronRight className="icon" size={20} aria-hidden="true" />
          </button>
        </div>
        {anchor !== today && (
          <button className="staff-text-button" type="button" onClick={() => setAnchor(today)}>
            Today
          </button>
        )}
      </div>
      <StaffState loading={bookings.loading && !bookings.data} error={bookings.error || schedule.error} onRetry={() => { bookings.reload(); schedule.reload() }} />
      {bookings.data && (
        <div className="calendar-grid" style={{ "--days": Math.max(shown.length, 1) } as CSSProperties}>
          {shown.length === 0 && <p className="calendar-free">You don't work this week.</p>}
          {shown.map((date) => {
            const hours = hoursOn(schedule.data, date)
            const own = all.filter((b) => b.date === date)
            return (
              <section key={date} className={`calendar-day${date === today ? " is-today" : ""}`} aria-label={longDate(date)}>
                <header>
                  <strong>
                    {DAY_NAMES[weekdayOf(date)]} {Number(date.slice(8))}
                    {date === today && <span className="visually-hidden"> (today)</span>}
                  </strong>
                  <span>{hours.off ? "Day off" : hours.text}</span>
                </header>
                <div>
                  {hours.off && (
                    <button type="button" className="calendar-block block-off" disabled>
                      {hours.text && <strong>{hours.text}</strong>}
                      <small>{hours.note ? `Day off: ${hours.note}` : "Day off"}</small>
                    </button>
                  )}
                  {own.map((b) => (
                    <button
                      key={b.id}
                      type="button"
                      className={`calendar-block ${blockClass[b.type]}${selected?.id === b.id ? " is-selected" : ""}${b.awaitingStatus || b.depositOverdue ? " needs-attention" : ""}`}
                      onClick={() => setSelected(b)}
                    >
                      <strong>
                        {b.time} to {b.endTime}
                      </strong>
                      <span>{b.anonymized ? "Customer" : (b.customerName ?? "").split(" ")[0]}</span>
                      <small>{bookingTitle(b)}</small>
                      {b.status === "PENDING" && <small className="block-note">Waiting for the deposit</small>}
                      {b.awaitingStatus && <small className="block-note">Mark Done or No-show</small>}
                      {b.depositOverdue && b.status !== "PENDING" && <small className="block-note">Deposit overdue</small>}
                      {(b.status === "DONE" || b.status === "NO_SHOW") && (
                        <small className="block-note">{b.status === "DONE" ? "Done" : "No-show"}</small>
                      )}
                    </button>
                  ))}
                  {!hours.off && own.length === 0 && <p className="calendar-free">No bookings</p>}
                </div>
              </section>
            )
          })}
        </div>
      )}
      <div className="calendar-legend">
        <span><i className="legend-flash"></i>Flash</span>
        <span><i className="legend-consultation"></i>Consultation</span>
        <span><i className="legend-reserved"></i>Reserved session</span>
        <span><i className="legend-off"></i>Day off</span>
      </div>

      {selected && (
        <StaffBookingPanel
          booking={selected}
          admin={false}
          onClose={() => setSelected(null)}
          onChanged={(updated, text) => {
            setNote(text)
            setSelected(updated)
            bookings.reload()
          }}
          onReserve={(b) => {
            setSelected(null)
            setReserving({
              customer: { bookingId: b.id, code: b.code, name: b.customerName ?? "", phone: b.phone ?? "", email: b.email, lastVisit: b.date, about: b.consultation?.idea ?? null },
              date: today,
            })
          }}
        />
      )}
      {reserving && (
        <ReservePanel
          customer={reserving.customer}
          date={reserving.date}
          onClose={() => setReserving(null)}
          onReserved={(b) => {
            setReserving(null)
            setNote(
              `Session reserved for ${b.customerName ?? "the customer"} on ${longDate(b.date)}, ${b.time} to ${b.endTime}.` +
                (b.deposit.status === "WAITING" ? " They get an email to pay the deposit within 72 hours." : ""),
            )
            setView("week")
            setAnchor(b.date)
            bookings.reload()
          }}
        />
      )}
    </>
  )
}

/** Reserve time for a session: pick the customer (from consultations and sessions), the time and the price. */
function ReservePanel({
  customer: initial,
  date: initialDate,
  onClose,
  onReserved,
}: {
  customer: CustomerOption | null
  date: string
  onClose: () => void
  onReserved: (b: StaffBooking) => void
}) {
  const [customer, setCustomer] = useState<CustomerOption | null>(initial)
  const [query, setQuery] = useState("")
  const [typed, setTyped] = useState("")
  const [values, setValues] = useState({ date: initialDate, start: "11:00", end: "15:00", description: "", price: "", deposit: "" })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  // Search as they type, a moment after they stop
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(typed.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [typed])
  const staff = useStaff()
  const options = useLoad<CustomerOption[]>(customer ? null : `/staff/customers?q=${encodeURIComponent(query)}`, staff?.token)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!customer) found.customer = "Choose the customer."
    if (!values.date) found.date = "Choose the date."
    else if (values.date < todayInSofia()) found.date = "Choose today or a later date."
    if (values.end <= values.start) found.end = "The end must be after the start."
    if (!values.description.trim()) found.description = "Describe the session, e.g. Back piece, session 1 of 3."
    const price = Number(values.price)
    const deposit = Number(values.deposit || 0)
    if (values.price === "" || !(price >= 0)) found.price = "Enter the price."
    if (!(deposit >= 0)) found.deposit = "Enter the deposit (0 for none)."
    else if (deposit > price) found.deposit = "The deposit can't be more than the price."
    setErrors(found)
    if (Object.keys(found).length) {
      document.querySelector<HTMLElement>(`.side-panel [name="${Object.keys(found)[0]}"]`)?.focus()
      return
    }
    setBusy(true)
    setProblem("")
    try {
      onReserved(
        await staffApi<StaffBooking>(
          "/staff/sessions",
          json("POST", {
            customerBookingId: customer!.bookingId,
            date: values.date,
            start: values.start,
            end: values.end,
            description: values.description.trim(),
            price,
            deposit,
          }),
        ),
      )
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setErrors(reason.errors)
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  const times = halfHours.map((t) => ({ value: t, label: t }))
  return (
    <SidePanel title="Reserve time" label="Reserve time" onClose={onClose}>
      <form className="panel-form" onSubmit={submit} noValidate>
        <FormError>{problem}</FormError>
        {customer ? (
          <div className="field">
            Customer
            <div className="chosen-customer">
              <span>
                <span>
                  <strong>{customer.name}</strong>, {customer.phone}
                </span>
                {customer.about && <small>{customer.about}</small>}
              </span>
              {!initial && (
                <button className="staff-text-button on-paper-link" type="button" onClick={() => setCustomer(null)}>
                  Change
                </button>
              )}
            </div>
          </div>
        ) : (
          <div className={`field${errors.customer ? " has-error" : ""}`}>
            <label htmlFor="customer-search">Customer</label>
            <span className="search-input">
              <Search className="icon" size={18} aria-hidden="true" />
              <input id="customer-search" name="customer" placeholder="Search by name or phone" value={typed}
                onChange={(e) => setTyped(e.target.value)} autoComplete="off" aria-describedby="customer-note" />
            </span>
            {errors.customer && <span className="field-error" id="customer-note">{errors.customer}</span>}
            <ul className="customer-options" aria-label="Matching customers">
              {options.data?.slice(0, 8).map((o) => (
                <li key={o.bookingId}>
                  <button type="button" onClick={() => setCustomer(o)}>
                    <span>
                      <strong>{o.name}</strong>, {o.phone}
                    </span>
                    {o.about && <small>{o.about}</small>}
                  </button>
                </li>
              ))}
              {options.data && options.data.length === 0 && (
                <li className="customer-none">
                  {query ? "Nobody matches. Sessions are for customers from your consultations." : "No customers yet: sessions follow a consultation."}
                </li>
              )}
            </ul>
          </div>
        )}
        <TextField label="Date" name="date" type="date" min={todayInSofia()} value={values.date} onChange={set("date")} error={errors.date} />
        <div className="panel-row">
          <SelectField label="Start time" name="start" options={times} value={values.start} onChange={set("start")} error={errors.start} />
          <SelectField label="End time" name="end" options={times} value={values.end} onChange={set("end")} error={errors.end} />
        </div>
        <TextField label="Description" name="description" placeholder="Back piece, session 1 of 3" maxLength={limits["session.description"]}
          value={values.description} onChange={set("description")} error={errors.description} />
        <div className="panel-row">
          <TextField label="Price €" name="price" type="number" inputMode="decimal" min={0} value={values.price} onChange={set("price")} error={errors.price} />
          <TextField label="Deposit €" name="deposit" type="number" inputMode="decimal" min={0} value={values.deposit} onChange={set("deposit")} error={errors.deposit} />
        </div>
        <p className="panel-helper">
          This time is removed from online booking and the customer sees the session in their bookings. With a deposit, they get an
          email to pay it within 72 hours.
        </p>
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Reserving..." : "Reserve time"}
        </button>
      </form>
    </SidePanel>
  )
}
