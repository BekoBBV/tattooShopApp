import { ImagePlus, X } from "lucide-react"
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react"
import { Link, Navigate, useNavigate, useSearchParams } from "react-router"
import { PageIntro } from "../components/Layout"
import {
  Art,
  ArtistSeal,
  ConsentCheck,
  FormError,
  SelectField,
  StepBar,
  TextAreaField,
  TextField,
} from "../components/Studio"
import { api, ApiError } from "../lib/api"
import { startDraft, updateDraft, useBookingDraft } from "../lib/bookingDraft"
import { whenOf } from "../lib/bookingText"
import { useCustomer } from "../lib/customer"
import { colours, countdown, dayButton, euros, hours, longDate, parseTypedDate, placements, size, todayInSofia } from "../lib/format"
import { limits } from "../lib/limits"
import type { Account, Artist, Availability, Booking, CreateConsultation, CreateFlashBooking, Flash, PaymentStart } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"
import { checkDetails, useFieldChecks } from "../lib/validation"

const MAX_PHOTOS = 5
const MAX_PHOTO_BYTES = 10 * 1024 * 1024

// ---- Step 1: Choose ----

/** Step 1: a flash design (chosen on the flash sheet) or a free consultation with an artist. */
export function BookChoose() {
  usePageTitle("Choose what to book")
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const draft = useBookingDraft()
  const artists = useLoad<Artist[]>("/artists")
  const chosenFlash = useLoad<Flash>(draft.flashId ? `/flash/${draft.flashId}` : null)
  const [problem, setProblem] = useState("")

  // A link may bring the choice along: ?flash=<id>, or ?type=consultation (&artist=<id>)
  const linkFlash = params.get("flash")
  const linkConsultation = params.get("type") === "consultation"
  const linkArtist = params.get("artist")
  useEffect(() => {
    if (linkFlash && linkFlash !== draft.flashId) startDraft({ kind: "flash", flashId: linkFlash })
    else if (linkFlash && draft.kind !== "flash") updateDraft({ kind: "flash" })
    else if (linkConsultation && (draft.kind !== "consultation" || (linkArtist && linkArtist !== draft.artistId))) {
      startDraft({ kind: "consultation", artistId: linkArtist ?? draft.artistId, flashId: draft.flashId })
    }
    // Only when the link changes; the draft is the result
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [linkFlash, linkConsultation, linkArtist])

  const flash = chosenFlash.data
  const pick = (kind: "flash" | "consultation") => {
    setProblem("")
    if (kind !== draft.kind) updateDraft({ kind, date: null, time: null })
  }

  function proceed() {
    if (draft.kind === "flash" && (!flash || !flash.bookable)) {
      setProblem(flash ? "This design was just booked by someone else. Choose another design." : "Choose a design from the flash sheet first.")
      return
    }
    if (draft.kind === "consultation" && !draft.artistId) {
      setProblem("Choose the artist you'd like to talk to.")
      return
    }
    if (!draft.kind) {
      setProblem("Choose a flash design or a free consultation.")
      return
    }
    navigate("/book/time")
  }

  return (
    <main>
      <PageIntro title="Choose what to book">
        <p>Book a finished design or start a custom piece with a free conversation.</p>
      </PageIntro>
      <StepBar current={1} withDeposit={draft.kind !== "consultation"} />
      <div className="wrap booking-body">
        <FormError>{problem}</FormError>
        <div className="choice-grid">
          <section className={`slip choice-card on-paper${draft.kind === "flash" ? " is-selected" : ""}`} aria-label="A flash design">
            <label className={`choice-radio${draft.kind === "flash" ? " is-selected" : ""}`}>
              <input type="radio" className="visually-hidden" name="kind" checked={draft.kind === "flash"} onChange={() => pick("flash")} />
              <span className="radio-mark" aria-hidden="true"></span>
              <span>
                <strong>A flash design</strong>
                <small>Fixed design, time and price</small>
              </span>
            </label>
            {flash ? (
              <div className="selected-flash">
                <div className="flash-frame">
                  <Art image={flash.image} alt={flash.name} />
                </div>
                <div>
                  <div className="selected-title">
                    <h3>{flash.name}</h3>
                    <ArtistSeal name={flash.artistName} />
                  </div>
                  <p>By {flash.artistName}</p>
                  <dl className="slip-list">
                    <div>
                      <dt>Size</dt>
                      <dd>{size(flash.widthCm, flash.heightCm)}</dd>
                    </div>
                    <div>
                      <dt>Time</dt>
                      <dd>About {hours(flash.durationMinutes)}</dd>
                    </div>
                    <div>
                      <dt>Price</dt>
                      <dd>{euros(flash.price)}</dd>
                    </div>
                  </dl>
                  {!flash.bookable && <p className="field-error">This design was just booked by someone else.</p>}
                  <Link className="text-link choose-other" to="/flash">
                    Choose another design
                  </Link>
                </div>
              </div>
            ) : (
              <p>
                Pick a design on the <Link className="text-link" to="/flash">flash sheet</Link> and press Book.
              </p>
            )}
          </section>
          <section className={`slip choice-card on-paper${draft.kind === "consultation" ? " is-selected" : ""}`} aria-label="A free consultation">
            <label className={`choice-radio${draft.kind === "consultation" ? " is-selected" : ""}`}>
              <input
                type="radio"
                className="visually-hidden"
                name="kind"
                checked={draft.kind === "consultation"}
                onChange={() => pick("consultation")}
              />
              <span className="radio-mark" aria-hidden="true"></span>
              <span>
                <strong>A free consultation</strong>
                <small>30 minutes to plan a bigger or custom piece</small>
              </span>
            </label>
            <fieldset className="artist-choice-list">
              <legend>Choose your artist</legend>
              {(artists.data ?? []).map((artist) => (
                <label key={artist.id} className="artist-choice">
                  <input
                    type="radio"
                    className="visually-hidden"
                    name="artist"
                    checked={draft.kind === "consultation" && draft.artistId === artist.id}
                    onChange={() => {
                      setProblem("")
                      updateDraft({ kind: "consultation", artistId: artist.id, date: null, time: null })
                    }}
                  />
                  <span className={`radio-mark${draft.kind === "consultation" && draft.artistId === artist.id ? " is-on" : ""}`} aria-hidden="true"></span>
                  <ArtistSeal name={artist.name} />
                  <span>
                    <strong>{artist.name}</strong>
                    <small>{artist.style}</small>
                  </span>
                </label>
              ))}
            </fieldset>
          </section>
        </div>
        <div className="booking-continue">
          <button className="button button-primary" type="button" onClick={proceed}>
            Continue
          </button>
        </div>
      </div>
    </main>
  )
}

// ---- Step 2: Time ----

/** Step 2: the next days, then the free start times on the chosen day. */
export function BookTime() {
  usePageTitle("Choose a time")
  const navigate = useNavigate()
  const draft = useBookingDraft()
  const today = useMemo(() => todayInSofia(), [])
  const ready = draft.kind === "flash" ? !!draft.flashId : draft.kind === "consultation" && !!draft.artistId
  const query = draft.kind === "flash" ? `flashId=${draft.flashId}` : `artistId=${draft.artistId}`
  const availability = useLoad<Availability>(ready ? `/availability?${query}&from=${today}&days=31` : null)
  const artists = useLoad<Artist[]>("/artists")
  const [problem, setProblem] = useState("")

  const days = availability.data?.days ?? []
  const firstOpen = days.find((d) => d.slots.length > 0)?.date ?? days[0]?.date ?? null
  const selectedDate = draft.date && days.some((d) => d.date === draft.date) ? draft.date : firstOpen
  const day = days.find((d) => d.date === selectedDate)
  const artistName = artists.data?.find((a) => a.id === availability.data?.artistId)?.name ?? "the artist"
  const minutes = availability.data?.durationMinutes ?? 30
  const isConsultation = draft.kind === "consultation"

  if (!ready) return <Navigate to="/book/choose" replace />

  const intro = isConsultation
    ? `Times when ${artistName} is free for a 30-minute consultation.`
    : `Times when ${artistName} is free for about ${hours(minutes)}.`

  function proceed() {
    if (!draft.time || draft.date !== selectedDate) {
      setProblem("Choose a start time first.")
      return
    }
    navigate("/book/details")
  }

  return (
    <main>
      <PageIntro title="Choose a time">
        <p>{availability.data ? intro : "Loading the free times..."}</p>
      </PageIntro>
      <StepBar current={2} withDeposit={!isConsultation} />
      <div className="wrap booking-body">
        <FormError>{availability.error || problem}</FormError>
        <div className="day-strip" aria-label="Available days" tabIndex={0}>
          {days.map((d) => {
            const label = dayButton(d.date)
            return (
              <button
                key={d.date}
                type="button"
                className={`day-button${d.date === selectedDate ? " is-selected" : ""}`}
                disabled={d.closed}
                aria-pressed={d.date === selectedDate}
                onClick={() => {
                  setProblem("")
                  updateDraft({ date: d.date, time: null })
                }}
              >
                <span>{label.weekday}</span>
                <strong>{label.date}</strong>
                {d.closed ? <small>Closed</small> : d.slots.length === 0 && <small>Full</small>}
              </button>
            )
          })}
        </div>
        {day && (
          <section className="time-selection" aria-labelledby="day-title">
            <h2 id="day-title">{longDate(day.date)}</h2>
            {day.slots.length === 0 ? (
              <p className="state-note">
                {isConsultation
                  ? `${artistName} has no free time on this day. Try another day or another artist.`
                  : `${artistName} has no free ${hours(minutes).replace(" hours", "-hour").replace(" hour", "-hour")} slot on this day. Try another day or another artist's design.`}
              </p>
            ) : (
              <div className="time-grid">
                {day.slots.map((slot) => {
                  const chosen = draft.date === day.date && draft.time === slot.time
                  return (
                    <button
                      key={slot.time}
                      type="button"
                      className={`time-button${chosen ? " is-selected" : ""}`}
                      aria-pressed={chosen}
                      onClick={() => {
                        setProblem("")
                        updateDraft({ date: day.date, time: slot.time })
                      }}
                    >
                      {slot.time}
                    </button>
                  )
                })}
              </div>
            )}
            <p className="time-note">
              All times are start times. {isConsultation ? "The consultation takes 30 minutes." : `The session takes about ${hours(minutes)}.`}
            </p>
          </section>
        )}
        <div className="booking-continue">
          <button className="button button-primary" type="button" onClick={proceed}>
            Continue
          </button>
        </div>
      </div>
    </main>
  )
}

// ---- Step 3: Details ----

type Photo = { file: File; url: string }

/**
 * Cancels an earlier booking from this tab if it is still only held (deposit not paid), e.g. after
 * the customer went back and picked another time. Paid and confirmed bookings are left alone.
 */
async function releaseHold(code: string, phone: string) {
  try {
    const earlier = await api<Booking>("/bookings/lookup", { method: "POST", body: JSON.stringify({ code, phone }) })
    if (earlier.status === "PENDING") {
      await api<Booking>("/bookings/cancel", { method: "POST", body: JSON.stringify({ code, phone }) })
    }
  } catch {
    // Gone already, or the lookup failed: the new booking simply goes ahead
  }
  updateDraft({ code: null, phone: null })
}

/** Step 3: who is coming, the idea (consultations) and the confirmations. */
export function BookDetails() {
  usePageTitle("Your details")
  const navigate = useNavigate()
  const draft = useBookingDraft()
  const customer = useCustomer()
  const account = useLoad<Account>(customer ? "/account" : null, customer?.token)
  const kind = draft.kind
  const [consent, setConsent] = useState({ adult: false, consentForm: false, depositPolicy: false })
  const [photos, setPhotos] = useState<Photo[]>([])
  const [photoProblem, setPhotoProblem] = useState("")
  const [sending, setSending] = useState(false)
  const [problem, setProblem] = useState<{ text: string; back?: "time" | "flash" } | null>(null)
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const d = draft.details

  // A signed-in customer gets their details filled in (only into empty fields)
  useEffect(() => {
    const a = account.data
    if (!a) return
    updateDraft({
      details: {
        ...d,
        customerName: d.customerName || a.name,
        phone: d.phone || a.phone,
        email: d.email || a.email,
        dateOfBirth: d.dateOfBirth || (a.dateOfBirth ? a.dateOfBirth.split("-").reverse().join(".") : ""),
      },
    })
    // Once, when the account arrives
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [account.data])

  // Free the photo previews when leaving the page (a removed photo is freed when it is removed)
  const photosRef = useRef<Photo[]>([])
  photosRef.current = photos
  useEffect(() => () => photosRef.current.forEach((p) => URL.revokeObjectURL(p.url)), [])

  const form = { ...d, ...consent }
  const errors = kind ? checkDetails(form, kind, draft.date ?? todayInSofia()) : {}
  const checks = useFieldChecks(errors, serverErrors)

  if (!kind || !draft.date || !draft.time) return <Navigate to={kind ? "/book/time" : "/book/choose"} replace />

  const set = (field: keyof typeof d, value: string) => updateDraft({ details: { ...d, [field]: value } })

  function addPhotos(files: FileList | null) {
    setPhotoProblem("")
    const added = [...(files ?? [])]
    const tooBig = added.find((f) => f.size > MAX_PHOTO_BYTES)
    if (tooBig) {
      setPhotoProblem(`"${tooBig.name}" is larger than 10 MB.`)
      return
    }
    if (photos.length + added.length > MAX_PHOTOS) {
      setPhotoProblem(`You can add up to ${MAX_PHOTOS} photos.`)
      return
    }
    setPhotos([...photos, ...added.map((file) => ({ file, url: URL.createObjectURL(file) }))])
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setSending(true)
    setProblem(null)
    setServerErrors({})
    const dateOfBirth = parseTypedDate(d.dateOfBirth)!
    const common = {
      date: draft.date!,
      time: draft.time!,
      customerName: d.customerName.trim(),
      phone: d.phone.trim(),
      email: d.email.trim(),
      dateOfBirth,
      adult: consent.adult,
      consentForm: consent.consentForm,
    }
    try {
      // A flash booking made earlier in this tab and not paid still holds its time and design: let it go first
      if (draft.code && draft.phone) await releaseHold(draft.code, draft.phone)
      let booking: Booking
      if (kind === "flash") {
        const body: CreateFlashBooking = { ...common, flashId: draft.flashId!, notes: d.notes.trim() || null, depositPolicy: consent.depositPolicy }
        booking = await api<Booking>("/bookings/flash", { method: "POST", body: JSON.stringify(body), token: customer?.token })
      } else {
        const body: CreateConsultation = {
          ...common,
          artistId: draft.artistId!,
          idea: d.idea.trim(),
          placement: d.placement,
          sizeCm: d.sizeCm.trim() ? Number(d.sizeCm) : null,
          colour: d.colour,
        }
        const data = new FormData()
        data.append("booking", new Blob([JSON.stringify(body)], { type: "application/json" }))
        photos.forEach((p) => data.append("photos", p.file))
        booking = await api<Booking>("/bookings/consultation", { method: "POST", body: data, token: customer?.token })
      }
      updateDraft({ code: booking.code, phone: d.phone.trim() })
      navigate(kind === "flash" ? "/book/deposit" : `/booking/confirmed?code=${encodeURIComponent(booking.code)}`)
    } catch (reason) {
      if (reason instanceof ApiError) {
        if (reason.errors) setServerErrors(reason.errors)
        if (reason.code === "UNDER_AGE") setServerErrors({ dateOfBirth: reason.message })
        else if (reason.code === "INVALID_PHONE") setServerErrors({ phone: reason.message })
        else if (reason.code === "SLOT_UNAVAILABLE" || reason.code === "OUTSIDE_BOOKING_WINDOW") setProblem({ text: reason.message, back: "time" })
        else if (reason.code === "FLASH_NOT_AVAILABLE") setProblem({ text: reason.message, back: "flash" })
        else if (reason.code !== "VALIDATION_FAILED") setProblem({ text: reason.message })
      } else {
        setProblem({ text: "Something went wrong. Please try again." })
      }
    } finally {
      setSending(false)
    }
  }

  return (
    <main>
      <PageIntro title="Your details">
        <p>Tell us who is coming to the studio and confirm the essentials.</p>
      </PageIntro>
      <StepBar current={3} withDeposit={kind === "flash"} />
      <div className="wrap booking-body">
        {!customer && (
          <p className="guest-note">
            No account needed. Have an account? <Link to="/account/sign-in?next=/book/details">Sign in</Link> to fill this in faster.
          </p>
        )}
        {problem && (
          <FormError>
            {problem.text}{" "}
            {problem.back === "time" && <Link to="/book/time">Pick another time</Link>}
            {problem.back === "flash" && <Link to="/flash">Choose another design</Link>}
          </FormError>
        )}
        <p className="guest-note">
          {kind === "flash" ? "Flash design" : "Free consultation"}: {longDate(draft.date)}, {draft.time}.{" "}
          <Link to="/book/time">Change the time</Link>
        </p>
        <form className="slip form-slip on-paper" onSubmit={submit} onChange={checks.onChange} onBlur={checks.onBlur} noValidate>
          <div className="form-section">
            <div className="form-grid">
              <TextField label="Full name" name="customerName" autoComplete="name" maxLength={limits["booking.customerName"]}
                value={d.customerName} onChange={(e) => set("customerName", e.target.value)} error={checks.errorFor("customerName")} />
              <TextField label="Phone" name="phone" type="tel" autoComplete="tel" maxLength={limits["booking.phone"]}
                value={d.phone} onChange={(e) => set("phone", e.target.value)} error={checks.errorFor("phone")} />
              <TextField label="Email" name="email" type="email" autoComplete="email" maxLength={limits["booking.email"]}
                value={d.email} onChange={(e) => set("email", e.target.value)} error={checks.errorFor("email")} />
              <TextField label="Date of birth" name="dateOfBirth" inputMode="numeric" placeholder="dd.mm.yyyy" autoComplete="bday"
                value={d.dateOfBirth} onChange={(e) => set("dateOfBirth", e.target.value)} error={checks.errorFor("dateOfBirth")} />
            </div>
          </div>
          {kind === "consultation" && (
            <section className="form-section">
              <h2>About your idea</h2>
              <TextAreaField label="Your idea" name="idea" full rows={5} maxLength={limits["consultation.idea"]}
                value={d.idea} onChange={(e) => set("idea", e.target.value)} error={checks.errorFor("idea")} />
              <div className="form-grid three" style={{ marginTop: 20 }}>
                <SelectField label="Placement" name="placement" options={placements} value={d.placement}
                  onChange={(e) => set("placement", e.target.value)} />
                <TextField label="Approximate size in cm" name="sizeCm" type="number" min={1} max={200} inputMode="numeric"
                  value={d.sizeCm} onChange={(e) => set("sizeCm", e.target.value)} error={checks.errorFor("sizeCm")} />
                <SelectField label="Colour" name="colour" options={colours} value={d.colour} onChange={(e) => set("colour", e.target.value)} />
              </div>
              <div className="reference-upload">
                <div>
                  <h3>Reference photos</h3>
                  <p>Up to 5 JPG, PNG or HEIC photos, 10 MB each.</p>
                </div>
                <label className="button button-secondary file-button">
                  <ImagePlus className="icon" size={18} aria-hidden="true" /> Add photos
                  <input type="file" multiple accept="image/jpeg,image/png,image/heic,image/heif,.heic,.heif"
                    onChange={(e) => {
                      addPhotos(e.target.files)
                      e.target.value = ""
                    }} />
                </label>
                {photoProblem && <p className="field-error">{photoProblem}</p>}
                {photos.length > 0 && (
                  <div className="reference-grid">
                    {photos.map((p, i) => (
                      <figure key={p.url}>
                        <img src={p.url} alt="" />
                        <figcaption>{p.file.name}</figcaption>
                        <button type="button" aria-label={`Remove ${p.file.name}`} onClick={() => {
                          URL.revokeObjectURL(p.url)
                          setPhotos(photos.filter((_, j) => j !== i))
                        }}>
                          <X className="icon" size={16} aria-hidden="true" />
                        </button>
                      </figure>
                    ))}
                  </div>
                )}
              </div>
            </section>
          )}
          <section className="form-section">
            <h2>Consent</h2>
            <ConsentCheck name="adult" checked={consent.adult} onChange={(v) => setConsent({ ...consent, adult: v })} error={checks.errorFor("adult")}>
              I am 18 or older.
            </ConsentCheck>
            <ConsentCheck name="consentForm" checked={consent.consentForm} onChange={(v) => setConsent({ ...consent, consentForm: v })}
              error={checks.errorFor("consentForm")}>
              I have read and agree to the consent form.
            </ConsentCheck>
            <details className="consent-details">
              <summary>Read the consent form</summary>
              <div>
                <h3>Health and safety</h3>
                <p>
                  Tell your artist about medical conditions, medication, allergies, skin reactions, pregnancy or anything else that
                  may affect safe tattooing.
                </p>
                <p>
                  Do not drink alcohol for 24 hours before your appointment. The artist may refuse or stop the session if tattooing
                  would be unsafe.
                </p>
              </div>
            </details>
            {kind === "flash" && (
              <ConsentCheck name="depositPolicy" checked={consent.depositPolicy}
                onChange={(v) => setConsent({ ...consent, depositPolicy: v })} error={checks.errorFor("depositPolicy")}>
                I understand the deposit policy: the €30 deposit is taken off the price, refunded if I cancel at least 48 hours before,
                and kept if I cancel later.
              </ConsentCheck>
            )}
          </section>
          <div className="form-actions">
            <button className="button button-primary" type="submit" disabled={sending}>
              {sending ? "Booking..." : kind === "flash" ? "Continue" : "Book consultation"}
            </button>
          </div>
        </form>
      </div>
    </main>
  )
}

// ---- Step 4: Deposit ----

/** Step 4 (flash): the summary, the 15-minute hold and the button to the payment page. */
export function BookDeposit() {
  usePageTitle("Pay the deposit")
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const draft = useBookingDraft()
  const code = params.get("code") ?? draft.code
  const phone = draft.phone
  const [booking, setBooking] = useState<Booking | null>(null)
  const [problem, setProblem] = useState("")
  const [paying, setPaying] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!code || !phone) return
    let current = true
    api<Booking>("/bookings/lookup", { method: "POST", body: JSON.stringify({ code, phone }) })
      .then((b) => {
        if (!current) return
        if (b.deposit.status === "PAID") navigate(`/booking/confirmed?code=${encodeURIComponent(b.code)}`, { replace: true })
        setBooking(b)
      })
      .catch((reason) => current && setProblem(reason instanceof Error ? reason.message : "Something went wrong."))
    return () => {
      current = false
    }
  }, [code, phone, navigate])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  if (!code || !phone) return <Navigate to="/book/choose" replace />

  const left = booking?.deposit.dueAt ? countdown(booking.deposit.dueAt, now) : null
  const expired = booking !== null && (booking.status !== "PENDING" || left === null)

  async function pay() {
    setPaying(true)
    setProblem("")
    try {
      const start = await api<PaymentStart>("/bookings/deposit", { method: "POST", body: JSON.stringify({ code, phone }) })
      window.location.assign(start.checkoutUrl)
    } catch (reason) {
      setPaying(false)
      if (reason instanceof ApiError && reason.code === "HOLD_EXPIRED") {
        setBooking(booking && { ...booking, status: "EXPIRED" })
        return
      }
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
    }
  }

  return (
    <main>
      <PageIntro title="Pay the deposit">
        <p>Review the booking and secure the appointment with a {euros(booking?.deposit.amount ?? 30)} deposit.</p>
      </PageIntro>
      <StepBar current={4} locked={booking !== null && !expired} />
      <div className="wrap booking-body">
        <FormError>{problem}</FormError>
        {booking && (
          <div className="deposit-layout">
            <section className="slip on-paper" aria-labelledby="summary-title">
              <h2 id="summary-title">Booking summary</h2>
              <dl className="slip-list">
                <div>
                  <dt>Design</dt>
                  <dd>{booking.title}</dd>
                </div>
                <div>
                  <dt>Artist</dt>
                  <dd>{booking.artistName}</dd>
                </div>
                <div>
                  <dt>Date and time</dt>
                  <dd>{whenOf(booking)}</dd>
                </div>
                <div>
                  <dt>Price</dt>
                  <dd>{euros(booking.price)}</dd>
                </div>
                <div>
                  <dt>Deposit</dt>
                  <dd>{euros(booking.deposit.amount)}</dd>
                </div>
                <div className="total">
                  <dt>Pay in the studio</dt>
                  <dd>{euros(booking.leftToPay)}</dd>
                </div>
              </dl>
            </section>
            <section aria-label="Payment">
              {expired ? (
                <>
                  <p className="hold-countdown" role="status">
                    The hold expired and the time is free again. Pick a time again to continue.
                  </p>
                  <Link className="button button-primary payment-button" to="/book/time" onClick={() => updateDraft({ time: null, code: null })}>
                    Pick a time
                  </Link>
                </>
              ) : (
                <>
                  <p className="hold-countdown">
                    We're holding this time for you for <strong aria-live="off">{left ?? "15:00"}</strong>
                  </p>
                  <button className="button button-primary payment-button" type="button" onClick={pay} disabled={paying}>
                    {paying ? "Taking you to Revolut to pay..." : `Pay ${euros(booking.deposit.amount)} deposit with Revolut`}
                  </button>
                  <p className="payment-methods">You can pay with Revolut Pay, a card, Apple Pay or Google Pay.</p>
                </>
              )}
            </section>
          </div>
        )}
        {!booking && !problem && <p className="state-note">Loading your booking...</p>}
      </div>
    </main>
  )
}
