import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react"
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Check,
  Clock3,
  Copy,
  KeyRound,
  MapPin,
  ShieldCheck,
  SprayCan,
} from "lucide-react"
import {
  Navigate,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router"
import { MiniDisc, SprayDisc } from "../components/brand"
import {
  AppLink,
  BareButton,
  Button,
  Checkbox,
  Eyebrow,
  Heading,
  Input,
  Textarea,
} from "../components/ui"
import { useBooking } from "../features/booking/useBooking"
import { ApiError, api } from "../lib/api"
import { useCustomer } from "../lib/customer"
import { formatAppointment, formatMoney, shop } from "../lib/data"
import { errorText, useLoad } from "../lib/useLoad"
import { checkBooking, useFieldChecks } from "../lib/validation"
import type {
  Account,
  Availability,
  Artist,
  Booking,
  Day,
  ErrorCode,
  Service,
} from "../lib/types"
import { usePageTitle } from "../lib/usePageTitle"
import { limits } from "../lib/limits"

const steps = ["Service", "Artist", "Time", "Details"]
const weekdays: Day[] = [
  "SUNDAY",
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
]
const initials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
type Catalog = { services: Service[]; artists: Artist[] }
/** Kept this long, so the four steps share one download instead of fetching it on every step. */
const CATALOG_MAX_AGE = 5 * 60_000
type CatalogDownload = { at: number; promise: Promise<Catalog>; data?: Catalog }
let catalogCache: CatalogDownload | null = null

const isFresh = (cache: CatalogDownload | null): cache is CatalogDownload =>
  !!cache && Date.now() - cache.at <= CATALOG_MAX_AGE

function loadCatalog(): Promise<Catalog> {
  if (!isFresh(catalogCache)) {
    const entry: CatalogDownload = {
      at: Date.now(),
      promise: Promise.all([api<Service[]>("/services"), api<Artist[]>("/artists")]).then(
        ([services, artists]) => {
          entry.data = { services, artists }
          return entry.data
        },
      ),
    }
    // A failed download is not kept: "Try again" fetches it anew
    entry.promise.catch(() => {
      if (catalogCache === entry) catalogCache = null
    })
    catalogCache = entry
  }
  return catalogCache!.promise
}

function useCatalog() {
  // Already downloaded: shown straight away, without a loading screen between steps
  const [state, setState] = useState<{ data?: Catalog; error: string }>(() => ({
    data: isFresh(catalogCache) ? catalogCache.data : undefined,
    error: "",
  }))
  useEffect(() => {
    if (state.data) return
    let current = true
    loadCatalog()
      .then((data) => current && setState({ data, error: "" }))
      .catch((reason) => current && setState({ error: errorText(reason) }))
    return () => {
      current = false
    }
  }, [state.data])
  return {
    services: state.data?.services ?? [],
    artists: state.data?.artists ?? [],
    loading: !state.data && !state.error,
    error: state.error,
  }
}
function BookingHeader({ current }: { current: number }) {
  return (
    <header className="booking-header">
      <AppLink to="/" className="brand">
        TATTOO <span>STUDIO</span>
      </AppLink>
      <div
        className="booking-progress"
        aria-label={`Booking step ${current + 1} of 4`}
      >
        {steps.map((step, index) => (
          <div className={index <= current ? "active" : ""} key={step}>
            <span>{index < current ? <Check /> : index + 1}</span>
            <b>{step}</b>
          </div>
        ))}
      </div>
      <span className="booking-count">0{current + 1}/04</span>
    </header>
  )
}
function Summary({
  services,
  artists,
}: {
  services: Service[]
  artists: Artist[]
}) {
  const { draft } = useBooking()
  const service = services.find((item) => item.id === draft.serviceId)
  const artist = artists.find((item) => item.id === draft.artistId)
  return (
    <aside className="booking-summary">
      <span className="summary-label">Now playing</span>
      <div className={`summary-disc palette-${service?.palette || "blue"}`}>
        <span />
      </div>
      <Heading level={3}>Your booking</Heading>
      <dl>
        <div>
          <dt>Service</dt>
          <dd>{service?.name || "Not selected"}</dd>
        </div>
        <div>
          <dt>Artist</dt>
          <dd>
            {draft.artistId === "any"
              ? "First available"
              : artist?.name || "Not selected"}
          </dd>
        </div>
        <div>
          <dt>Time</dt>
          <dd>
            {draft.slot
              ? formatAppointment(draft.slot.startsAt)
              : "Not selected"}
          </dd>
        </div>
        <div>
          <dt>Total</dt>
          <dd>{service ? formatMoney(service.price) : "—"}</dd>
        </div>
      </dl>
      <p>
        <ShieldCheck /> Pay at the shop. Cancel online up to 2 hours before.
      </p>
    </aside>
  )
}
function BookingShell({
  step,
  children,
  services,
  artists,
}: {
  step: number
  children: ReactNode
  services: Service[]
  artists: Artist[]
}) {
  return (
    <main className="booking-page">
      <BookingHeader current={step} />
      <div className="booking-layout">
        <section className="booking-content">{children}</section>
        <Summary services={services} artists={artists} />
      </div>
    </main>
  )
}
function CatalogState({ loading, error }: { loading: boolean; error: string }) {
  if (loading)
    return (
      <main className="result-page">
        <div className="spinner" />
      </main>
    )
  if (error)
    return (
      <main className="result-page">
        <div className="result-card">
          <Heading level={2}>Couldn’t load the cut list.</Heading>
          <p>{error}</p>
          <Button onClick={() => location.reload()}>Try again</Button>
        </div>
      </main>
    )
  return null
}
export function ServiceStep() {
  usePageTitle("Choose a service")
  const { draft, update } = useBooking()
  const navigate = useNavigate()
  const catalog = useCatalog()
  const state = <CatalogState loading={catalog.loading} error={catalog.error} />
  if (catalog.loading || catalog.error) return state
  return (
    <BookingShell step={0} {...catalog}>
      <Eyebrow>Step one</Eyebrow>
      <Heading level={1}>Choose your cut.</Heading>
      <p className="page-intro">
        Every service reserves the same one-hour chair slot.
      </p>
      <h2 className="sr-only">Services</h2>
      {catalog.services.length ? (
        <div className="choice-grid">
          {catalog.services.map((service) => (
            <BareButton
              className="choice-button"
              aria-pressed={draft.serviceId === service.id}
              // Keeps the artist: every artist offers every service, and "Book with …" pre-selects one
              onClick={() => update({ serviceId: service.id, slot: null })}
              key={service.id}
            >
              <MiniDisc
                className={`palette-${service.palette}`}
                label={service.label}
                selected={draft.serviceId === service.id}
              >
                <div className="choice-check">
                  {draft.serviceId === service.id ? <Check /> : null}
                </div>
                <div className="choice-copy">
                  <Heading level={3}>{service.name}</Heading>
                  <p>{service.description}</p>
                  <strong>{formatMoney(service.price)}</strong>
                </div>
              </MiniDisc>
            </BareButton>
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <Heading level={3}>No services available.</Heading>
        </div>
      )}
      <div className="booking-actions">
        <AppLink to="/" variant="ghost">
          <ArrowLeft /> Back home
        </AppLink>
        <Button
          disabled={!draft.serviceId}
          onClick={() => navigate("/book/artist")}
        >
          Choose artist <ArrowRight />
        </Button>
      </div>
    </BookingShell>
  )
}
export function ArtistStep() {
  usePageTitle("Choose an artist")
  const { draft, update } = useBooking()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const catalog = useCatalog()
  useEffect(() => {
    const id = params.get("artist")
    // A time picked for another artist doesn't carry over to this one
    if (id) update((d) => (d.artistId === id ? {} : { artistId: id, slot: null }))
  }, [params, update])
  if (!draft.serviceId) return <Navigate to="/book/service" replace />
  if (catalog.loading || catalog.error)
    return <CatalogState loading={catalog.loading} error={catalog.error} />
  return (
    <BookingShell step={1} {...catalog}>
      <Eyebrow>Step two</Eyebrow>
      <Heading level={1}>Pick your chair.</Heading>
      <p className="page-intro">Every artist offers every service.</p>
      <h2 className="sr-only">Artists</h2>
      <div className="artist-choice-grid">
        <BareButton
          className={`artist-choice accent-cyan ${
            draft.artistId === "any" ? "selected" : ""
          }`}
          aria-pressed={draft.artistId === "any"}
          onClick={() => update({ artistId: "any", slot: null })}
        >
          <SprayDisc accent="cyan" initials="ANY" />
          <div>
            <Heading level={3}>First available</Heading>
            <strong>Fastest way in</strong>
            <p>We’ll pair you with an open chair.</p>
          </div>
          <span className="radio">
            {draft.artistId === "any" ? <Check /> : null}
          </span>
        </BareButton>
        {catalog.artists.map((artist) => (
          <BareButton
            className={`artist-choice accent-${artist.accent} ${
              draft.artistId === artist.id ? "selected" : ""
            }`}
            aria-pressed={draft.artistId === artist.id}
            onClick={() => update({ artistId: artist.id, slot: null })}
            key={artist.id}
          >
            <SprayDisc
              accent={artist.accent}
              initials={initials(artist.name)}
            />
            <div>
              <Heading level={3}>{artist.name}</Heading>
              <strong>{artist.specialty}</strong>
              <p>{artist.bio}</p>
            </div>
            <span className="radio">
              {draft.artistId === artist.id ? <Check /> : null}
            </span>
          </BareButton>
        ))}
      </div>
      <div className="booking-actions">
        <AppLink to="/book/service" variant="ghost">
          <ArrowLeft /> Service
        </AppLink>
        <Button
          disabled={!draft.artistId}
          onClick={() => navigate("/book/time")}
        >
          Find a time <ArrowRight />
        </Button>
      </div>
    </BookingShell>
  )
}
/** "Tuesday 20 October", for the spoken labels of the time buttons */
const spokenDay = new Intl.DateTimeFormat("en-GB", {
  weekday: "long",
  day: "numeric",
  month: "long",
  timeZone: "UTC",
})

export function TimeStep() {
  usePageTitle("Pick a time")
  const { draft, update } = useBooking()
  const navigate = useNavigate()
  const catalog = useCatalog()
  const artistParam =
    draft.artistId === "any" ? "" : `&artistId=${draft.artistId}`
  const {
    data: availability,
    loading,
    error,
    reload: load,
  } = useLoad<Availability>(
    draft.serviceId && draft.artistId
      ? `/availability?serviceId=${draft.serviceId}${artistParam}&days=7`
      : null,
  )
  // Why a day has no times: nobody (or the chosen artist) works that weekday,
  // today's remaining times have passed, or every chair is taken.
  const emptyDayLabel = (date: string) => {
    const weekday = weekdays[new Date(`${date}T12:00:00Z`).getUTCDay()]
    // A date exception (day off or special hours) wins over the weekly hours
    const working = catalog.artists.filter((artist) => {
      const exception = artist.exceptions?.find((item) => item.date === date)
      if (exception) return !exception.closed
      return artist.weeklyHours.some((hours) => hours.day === weekday)
    })
    if (!working.length) return "Closed"
    if (
      draft.artistId !== "any" &&
      !working.some((artist) => artist.id === draft.artistId)
    )
      return "Day off"
    if (date === availability?.days[0]?.date) return "No more times today"
    return "Fully booked"
  }
  if (!draft.serviceId || !draft.artistId)
    return <Navigate to="/book/artist" replace />
  if (catalog.loading || catalog.error)
    return <CatalogState loading={catalog.loading} error={catalog.error} />
  return (
    <BookingShell step={2} {...catalog}>
      <Eyebrow>Step three</Eyebrow>
      <Heading level={1}>Lock your time.</Heading>
      <p className="page-intro">
        Seven days at once. Times are 24-hour Europe/Sofia shop time.
      </p>
      {loading ? (
        <div className="loading-panel">
          <div className="spinner" />
          <p>Checking the chairs…</p>
        </div>
      ) : null}
      {error ? (
        <div className="error-panel">
          <Heading level={3}>Couldn’t load times</Heading>
          <p>{error}</p>
          <Button onClick={load}>Try again</Button>
        </div>
      ) : null}
      <h2 className="sr-only">Available times</h2>
      {!loading && !error ? (
        <div className="slot-days">
          {availability?.days.map((day) => (
            <div className="slot-day" key={day.date}>
              <div>
                <CalendarDays />
                <Heading level={3}>
                  {new Intl.DateTimeFormat("en-GB", {
                    weekday: "short",
                    day: "numeric",
                    month: "short",
                    timeZone: "UTC",
                  }).format(new Date(`${day.date}T12:00:00Z`))}
                </Heading>
              </div>
              {day.slots.length ? (
                <div className="slot-grid">
                  {day.slots.map((slot) => (
                    <BareButton
                      className={
                        draft.slot?.startsAt === slot.startsAt ? "selected" : ""
                      }
                      aria-pressed={draft.slot?.startsAt === slot.startsAt}
                      aria-label={`${slot.time} on ${spokenDay.format(new Date(`${day.date}T12:00:00Z`))}`}
                      onClick={() =>
                        update({ slot: { ...slot, date: day.date } })
                      }
                      key={slot.startsAt}
                    >
                      <Clock3 /> {slot.time}
                    </BareButton>
                  ))}
                </div>
              ) : (
                <p>{emptyDayLabel(day.date)}</p>
              )}
            </div>
          ))}
        </div>
      ) : null}
      <div className="booking-actions">
        <AppLink to="/book/artist" variant="ghost">
          <ArrowLeft /> Artist
        </AppLink>
        <Button
          disabled={!draft.slot}
          onClick={() => navigate("/book/details")}
        >
          Add details <ArrowRight />
        </Button>
      </div>
    </BookingShell>
  )
}
export function DetailsStep() {
  usePageTitle("Your details")
  const { draft, update } = useBooking()
  const navigate = useNavigate()
  const catalog = useCatalog()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [errorCode, setErrorCode] = useState<ErrorCode | undefined>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  // Blocks a double click from sending the booking twice before the button re-renders as disabled
  const submitting = useRef(false)
  const [agreed, setAgreed] = useState(false)
  const customer = useCustomer()
  // Signed in: fill in the empty fields from the account (once per visit to this step)
  const customerToken = customer?.token
  useEffect(() => {
    if (!customerToken) return
    let current = true
    api<Account>("/account", { token: customerToken })
      .then((account) => {
        if (!current) return
        update((d) => ({
          customer: {
            ...d.customer,
            name: d.customer.name || account.name,
            phone: d.customer.phone || account.phone,
            email: d.customer.email || account.email,
          },
        }))
      })
      .catch(() => {
        // Not filled in: the customer types their details as a guest would
      })
    return () => {
      current = false
    }
  }, [customerToken, update])
  const checks = useFieldChecks(
    {
      ...checkBooking(draft.customer),
      ...(agreed ? {} : { policy: "Please tick this box to continue." }),
    },
    fieldErrors,
  )
  if (!draft.serviceId || !draft.artistId || !draft.slot)
    return <Navigate to="/book/time" replace />
  if (catalog.loading || catalog.error)
    return <CatalogState loading={catalog.loading} error={catalog.error} />
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    if (submitting.current) return
    submitting.current = true
    setLoading(true)
    setError("")
    setErrorCode(undefined)
    setFieldErrors({})
    try {
      const body = JSON.stringify({
        serviceId: draft.serviceId,
        artistId: draft.artistId === "any" ? null : draft.artistId,
        date: draft.slot!.date,
        time: draft.slot!.time,
        customerName: draft.customer.name,
        phone: draft.customer.phone,
        email: draft.customer.email || null,
        notes: draft.customer.notes || null,
      })
      // Signed in: the booking also goes to My bookings (a sign-in the server no longer accepts
      // is ignored there, so the booking then simply goes through as a guest booking)
      const result = await api<Booking>("/bookings", { method: "POST", token: customer?.token, body })
      try {
        // Lets the confirmation survive a page refresh; optional if storage is blocked
        sessionStorage.setItem(
          "tattoo-studio-last-confirmation",
          JSON.stringify(result),
        )
      } catch {
        // Storage unavailable: the confirmation still arrives via navigation state
      }
      navigate("/booking/confirmed", { state: result })
    } catch (reason) {
      if (reason instanceof ApiError) {
        setErrorCode(reason.code)
        setFieldErrors(reason.errors || {})
      }
      setError(reason instanceof Error ? reason.message : "Booking failed.")
    } finally {
      submitting.current = false
      setLoading(false)
    }
  }
  return (
    <BookingShell step={3} {...catalog}>
      <Eyebrow>Final step</Eyebrow>
      <Heading level={1}>Put your name on it.</Heading>
      <p className="page-intro">
        {customer ? (
          <>
            Signed in as {customer.user.name || customer.user.username}: this
            booking goes to <AppLink to="/account">My bookings</AppLink>.
          </>
        ) : (
          <>
            Add your email (optional) and we’ll send your confirmation and
            booking code there too. Have an account?{" "}
            <AppLink to="/account/sign-in" state={{ back: "/book/details" }}>
              Sign in
            </AppLink>{" "}
            to fill this in.
          </>
        )}
      </p>
      <form
        className="details-form"
        noValidate
        onSubmit={submit}
        onBlur={checks.onBlur}
        onChange={checks.onChange}
      >
        <div className="form-grid">
          <Input
            label="Full name"
            name="customerName"
            autoComplete="name"
            required
            maxLength={limits["booking.customerName"]}
            value={draft.customer.name}
            error={checks.errorFor("customerName")}
            onChange={(e) =>
              update({ customer: { ...draft.customer, name: e.target.value } })
            }
          />
          <Input
            label="Phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            required
            maxLength={limits["booking.phone"]}
            value={draft.customer.phone}
            error={checks.errorFor("phone")}
            onChange={(e) =>
              update({ customer: { ...draft.customer, phone: e.target.value } })
            }
          />
          <Input
            label="Email (optional)"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={limits["booking.email"]}
            value={draft.customer.email}
            error={checks.errorFor("email")}
            onChange={(e) =>
              update({ customer: { ...draft.customer, email: e.target.value } })
            }
          />
        </div>
        <Textarea
          label="Anything your artist should know?"
          name="notes"
          maxLength={limits["booking.notes"]}
          error={checks.errorFor("notes")}
          rows={4}
          value={draft.customer.notes}
          onChange={(e) =>
            update({ customer: { ...draft.customer, notes: e.target.value } })
          }
        />
        <Checkbox
          className="policy-check"
          name="policy"
          required
          checked={agreed}
          onChange={(e) => setAgreed(e.target.checked)}
          aria-invalid={checks.errorFor("policy") ? true : undefined}
          label={
            <span>
              <ShieldCheck /> I understand I can cancel online up to 2 hours
              before; payment is at the shop.
            </span>
          }
        />
        {checks.errorFor("policy") ? (
          <small className="field-error">{checks.errorFor("policy")}</small>
        ) : null}
        {error ? (
          <div className="form-error">
            <p>{error}</p>
            {errorCode === "SLOT_UNAVAILABLE" ? (
              <AppLink to="/book/time">Back to step 3</AppLink>
            ) : null}
            {errorCode === "PHONE_LIMIT_REACHED" ? (
              <AppLink to="/cancel">Manage booking</AppLink>
            ) : null}
          </div>
        ) : null}
        <div className="booking-actions">
          <AppLink to="/book/time" variant="ghost">
            <ArrowLeft /> Time
          </AppLink>
          <Button type="submit" loading={loading}>
            Confirm booking <Check />
          </Button>
        </div>
      </form>
    </BookingShell>
  )
}
export function Confirmation() {
  const customer = useCustomer()
  usePageTitle("Booking confirmed")
  const location = useLocation()
  const { reset } = useBooking()
  const [copied, setCopied] = useState<"" | "copied" | "failed">("")
  let booking = location.state as Booking | null
  if (!booking) {
    try {
      booking = JSON.parse(
        sessionStorage.getItem("tattoo-studio-last-confirmation") || "null",
      )
    } catch {
      booking = null
    }
  }
  useEffect(() => reset(), [reset])
  if (!booking) return <Navigate to="/book/service" replace />
  const current = booking
  async function copy() {
    try {
      // Not available on plain http (except localhost) or when the browser denies it
      await navigator.clipboard.writeText(current.code)
      setCopied("copied")
    } catch {
      setCopied("failed")
    }
    window.setTimeout(() => setCopied(""), 2500)
  }
  return (
    <main className="result-page">
      <div className="result-card confirmation-card">
        <div className="success-mark">
          <Check />
        </div>
        <Eyebrow>Chair locked</Eyebrow>
        <div className="paint-reveal">
          <span className="paint-stroke" />
          <SprayCan className="paint-can" />
          <Heading level={1}>You’re on the list.</Heading>
        </div>
        <div className="receipt">
          <div>
            <KeyRound />
            <span>
              <small>Booking code</small>
              {current.code}
            </span>
          </div>
          <div>
            <Clock3 />
            <span>
              <small>Date & time</small>
              {formatAppointment(current.startsAt)}
            </span>
          </div>
          <div>
            <SprayDisc initials={initials(current.artistName)} size="sm" />
            <span>
              <small>Service & artist</small>
              {current.serviceName} with {current.artistName}
            </span>
          </div>
          <div>
            <MapPin />
            <span>
              <small>Location</small>
              {shop.address}
            </span>
          </div>
          <div>
            <ShieldCheck />
            <span>
              <small>Due at shop</small>
              {formatMoney(current.price)}
            </span>
          </div>
        </div>
        <div className="cancellation-code">
          <span>Booking code</span>
          <strong>{current.code}</strong>
          <Button variant="ghost" onClick={copy}>
            <Copy />{" "}
            {copied === "copied"
              ? "Copied"
              : copied === "failed"
                ? "Couldn’t copy, please write it down"
                : "Copy code"}
          </Button>
          <p>
            {customer
              ? "It’s also in My bookings, where you can cancel it. "
              : "Save this code. You’ll need it together with your phone number to view or cancel your booking. "}
            Online cancellation is free until 2 hours before.
          </p>
        </div>
        <div className="confirmation-actions">
          {customer ? (
            <AppLink to="/account" variant="secondary">
              My bookings
            </AppLink>
          ) : (
            <AppLink to="/cancel" state={{ code: current.code }} variant="secondary">
              Manage booking
            </AppLink>
          )}
          <AppLink to="/" variant="primary">
            Back to Tattoo Studio
          </AppLink>
        </div>
      </div>
    </main>
  )
}
