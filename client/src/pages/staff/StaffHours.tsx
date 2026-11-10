import { Check } from "lucide-react"
import { useState, type FormEvent } from "react"
import { StaffHeader, StaffState } from "../../components/StaffUi"
import { Dialog, SelectField, TextField } from "../../components/Studio"
import { longDate, plainDate, todayInSofia } from "../../lib/format"
import { limits } from "../../lib/limits"
import { halfHours, json, staffApi, useStaff } from "../../lib/staff"
import { bookingTitle, DAY_NAMES } from "../../lib/staffText"
import type { DayException, Schedule, StaffBooking, Weekday } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

/** The rows in the order the studio's week runs: Tuesday first, Monday (usually closed) last. */
const ORDER: Weekday[] = ["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY", "MONDAY"]
const BREAKS = [0, 30, 60, 90, 120]

type Day = { off: boolean; start: string; end: string }

/** My hours: when customers can book with this artist, their days off and the break after each client. */
export default function StaffHours() {
  usePageTitle("My hours")
  const staff = useStaff()
  const schedule = useLoad<Schedule>("/staff/schedule", staff?.token)
  return (
    <>
      <StaffHeader title="My hours">Set the hours customers can book with you.</StaffHeader>
      <StaffState loading={schedule.loading && !schedule.data} error={schedule.error} onRetry={schedule.reload} />
      {/* Remount the form with the saved values whenever they are loaded again */}
      {schedule.data && <HoursForm key={JSON.stringify(schedule.data)} saved={schedule.data} />}
    </>
  )
}

function HoursForm({ saved }: { saved: Schedule }) {
  const [days, setDays] = useState<Record<Weekday, Day>>(() =>
    Object.fromEntries(
      ORDER.map((day) => {
        const h = saved.weeklyHours.find((w) => w.day === day)
        return [day, h ? { off: false, start: h.start, end: h.end } : { off: true, start: "11:00", end: "23:00" }]
      }),
    ) as Record<Weekday, Day>,
  )
  const [exceptions, setExceptions] = useState<DayException[]>(saved.exceptions)
  const [breakMinutes, setBreakMinutes] = useState(saved.breakMinutes)
  const [conflicts, setConflicts] = useState<StaffBooking[]>(saved.conflicts)
  const [changed, setChanged] = useState(false)
  const [status, setStatus] = useState("")
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)
  const [adding, setAdding] = useState(false)

  const touch = () => {
    setChanged(true)
    setStatus("")
  }
  const setDay = (day: Weekday, change: Partial<Day>) => {
    setDays({ ...days, [day]: { ...days[day], ...change } })
    touch()
  }
  const wrong = ORDER.filter((day) => !days[day].off && days[day].end <= days[day].start)

  async function save() {
    if (wrong.length) {
      setProblem(`The end time must be after the start time on ${wrong.map((d) => DAY_NAMES[d]).join(" and ")}.`)
      return
    }
    setBusy(true)
    setProblem("")
    try {
      const result = await staffApi<Schedule>(
        "/staff/schedule",
        json("PUT", {
          weeklyHours: ORDER.filter((day) => !days[day].off).map((day) => ({ day, start: days[day].start, end: days[day].end })),
          exceptions,
          breakMinutes,
        }),
      )
      setExceptions(result.exceptions)
      setConflicts(result.conflicts)
      setChanged(false)
      setStatus("Hours saved.")
    } catch (reason) {
      setProblem(errorText(reason))
    } finally {
      setBusy(false)
    }
  }

  const times = halfHours.map((t) => <option key={t} value={t}>{t}</option>)
  const upcoming = [...exceptions].sort((a, b) => a.date.localeCompare(b.date))

  return (
    <>
      <section className="staff-section">
        <h2>Weekly hours</h2>
        <div className="hours-table">
          {ORDER.map((day) => {
            const d = days[day]
            return (
              <div key={day} className={`hours-row${d.off ? " is-off" : ""}`}>
                <strong>{DAY_NAMES[day]}</strong>
                <label className="day-off-check">
                  <input type="checkbox" className="visually-hidden" checked={d.off} onChange={(e) => setDay(day, { off: e.target.checked })} />
                  <span className={`night-check${d.off ? " is-on" : ""}`} aria-hidden="true">
                    {d.off && <Check className="icon" size={16} aria-hidden="true" />}
                  </span>
                  Day off
                </label>
                <label className="time-field">
                  Start
                  <select value={d.start} disabled={d.off} onChange={(e) => setDay(day, { start: e.target.value })} aria-label={`${DAY_NAMES[day]} start`}>
                    {times}
                  </select>
                </label>
                <label className="time-field">
                  End
                  <select value={d.end} disabled={d.off} onChange={(e) => setDay(day, { end: e.target.value })} aria-label={`${DAY_NAMES[day]} end`}>
                    {times}
                  </select>
                </label>
              </div>
            )
          })}
        </div>
      </section>

      <section className="staff-section">
        <div className="staff-section-heading">
          <h2>Days off</h2>
          <button className="button button-secondary staff-compact" type="button" onClick={() => setAdding(true)}>
            Add day off
          </button>
        </div>
        <div className="days-off-list">
          {upcoming.length === 0 && <p className="staff-note">No days off or special hours coming up.</p>}
          {upcoming.map((e) => (
            <div key={e.date}>
              <strong>{plainDate(e.date)}</strong>
              <span>
                {e.closed ? "Day off" : `Open ${e.start} to ${e.end}`}
                {e.note ? `: ${e.note}` : ""}
              </span>
              <button
                className="staff-text-button"
                type="button"
                aria-label={`Remove ${plainDate(e.date)}`}
                onClick={() => {
                  setExceptions(exceptions.filter((x) => x.date !== e.date))
                  touch()
                }}
              >
                Remove
              </button>
            </div>
          ))}
        </div>
      </section>

      <section className="staff-section">
        <div className="break-setting">
          <label htmlFor="break-minutes">Break after each client</label>
          <span className="time-field">
            <select id="break-minutes" className="night-input" value={breakMinutes}
              onChange={(e) => { setBreakMinutes(Number(e.target.value)); touch() }}>
              {BREAKS.map((m) => <option key={m} value={m}>{m}</option>)}
            </select>
            minutes
          </span>
        </div>
      </section>

      {conflicts.length > 0 && (
        <section className="staff-section conflicts" aria-labelledby="conflicts-title">
          <h2 id="conflicts-title">Bookings outside your hours</h2>
          <p>These customer bookings fall outside your hours now. They stay booked until you or the studio cancel them.</p>
          <ul>
            {conflicts.map((b) => (
              <li key={b.id}>
                {longDate(b.date)}, {b.time} to {b.endTime}: {b.customerName ?? "Customer"} ({bookingTitle(b)})
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="save-bar">
        {problem ? (
          <p role="alert" className="save-problem">{problem}</p>
        ) : status ? (
          <p role="status"><Check className="icon" size={18} aria-hidden="true" /> {status}</p>
        ) : (
          changed && <p role="status">You have unsaved changes.</p>
        )}
        <button className="button button-primary" type="button" disabled={busy} onClick={save}>
          {busy ? "Saving..." : "Save hours"}
        </button>
      </div>

      {adding && (
        <DayOffDialog
          onClose={() => setAdding(false)}
          onAdd={(e) => {
            setExceptions([...exceptions.filter((x) => x.date !== e.date), e])
            setAdding(false)
            touch()
            setStatus("")
          }}
        />
      )}
    </>
  )
}

/** A day off, or one day with different hours (e.g. a convention, a late evening). */
function DayOffDialog({ onClose, onAdd }: { onClose: () => void; onAdd: (e: DayException) => void }) {
  const [values, setValues] = useState({ date: "", kind: "off", start: "11:00", end: "19:00", note: "" })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!values.date) found.date = "Choose the date."
    else if (values.date < todayInSofia()) found.date = "Choose today or a later date."
    if (values.kind === "hours" && values.end <= values.start) found.end = "The end must be after the start."
    setErrors(found)
    if (Object.keys(found).length) return
    const closed = values.kind === "off"
    onAdd({ date: values.date, closed, start: closed ? null : values.start, end: closed ? null : values.end, note: values.note.trim() || null })
  }

  const times = halfHours.map((t) => ({ value: t, label: t }))
  return (
    <Dialog title="Add a day off" titleId="day-off-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <TextField label="Date" name="date" type="date" min={todayInSofia()} value={values.date} onChange={set("date")} error={errors.date} />
      <SelectField label="That day" name="kind" value={values.kind} onChange={set("kind")}
        options={[{ value: "off", label: "Day off" }, { value: "hours", label: "Different hours" }]} />
      {values.kind === "hours" && (
        <div className="panel-row dialog-row">
          <SelectField label="Start" name="start" options={times} value={values.start} onChange={set("start")} />
          <SelectField label="End" name="end" options={times} value={values.end} onChange={set("end")} error={errors.end} />
        </div>
      )}
      <TextField label="Note (optional)" name="note" placeholder="Tattoo convention, Milan" maxLength={limits["exception.note"]}
        value={values.note} onChange={set("note")} helper="Only you and the studio see this." />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit">Add</button>
        <button className="button button-secondary" type="button" onClick={onClose}>Cancel</button>
      </div>
      <p className="dialog-hint">Remember to press Save hours afterwards.</p>
    </Dialog>
  )
}
