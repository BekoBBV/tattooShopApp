import { useState, type FormEvent } from "react"
import {
  CalendarX,
  Check,
  Clock3,
  KeyRound,
  MapPin,
  ShieldAlert,
} from "lucide-react"
import { useLocation } from "react-router"
import { SprayDisc } from "../components/brand"
import {
  AppLink,
  Button,
  Eyebrow,
  Heading,
  Input,
  StatusBadge,
} from "../components/ui"
import { ApiError, api } from "../lib/api"
import { formatAppointment, formatMoney, shop } from "../lib/data"
import type { Booking } from "../lib/types"
import { usePageTitle } from "../lib/usePageTitle"
import { checkLookup, useFieldChecks } from "../lib/validation"
import { limits } from "../lib/limits"

const initials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)

export function CancellationLookup() {
  usePageTitle("Find your booking")
  // The confirmation page hands the code over in navigation state, so it never appears in the URL
  const location = useLocation()
  const [code, setCode] = useState(
    (location.state as { code?: string } | null)?.code ?? "",
  )
  const [phone, setPhone] = useState("")
  const [booking, setBooking] = useState<Booking | null>(null)
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const checks = useFieldChecks(checkLookup(code, phone))
  async function lookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setLoading(true)
    setError("")
    try {
      setBooking(
        await api<Booking>("/bookings/lookup", {
          method: "POST",
          body: JSON.stringify({ code: code.trim().toUpperCase(), phone }),
        }),
      )
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === "TOO_MANY_ATTEMPTS")
        setError("Too many attempts. Try again in 15 minutes.")
      else
        setError(
          reason instanceof Error
            ? reason.message
            : "No booking found for this code and phone number",
        )
    } finally {
      setLoading(false)
    }
  }
  async function cancel() {
    setLoading(true)
    setError("")
    try {
      setBooking(
        await api<Booking>("/bookings/cancel", {
          method: "POST",
          body: JSON.stringify({ code, phone }),
        }),
      )
      setConfirming(false)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not cancel.")
    } finally {
      setLoading(false)
    }
  }
  if (booking) {
    return (
      <main className="result-page">
        <div className="result-card manage-card">
          {booking.status === "CANCELLED" ? (
            <div className="cancelled-mark">
              <CalendarX />
            </div>
          ) : (
            <SprayDisc initials={initials(booking.artistName)} size="lg" />
          )}
          <Eyebrow>Manage booking</Eyebrow>
          <Heading level={1}>
            {booking.status === "CANCELLED"
              ? "Your chair is released."
              : "Your chair is waiting."}
          </Heading>
          <StatusBadge status={booking.status} />
          <div className="receipt">
            <div>
              <Clock3 />
              <span>
                <small>Date & time</small>
                {formatAppointment(booking.startsAt)}
              </span>
            </div>
            <div>
              <SprayDisc initials={initials(booking.artistName)} size="sm" />
              <span>
                <small>Service</small>
                {booking.serviceName} with {booking.artistName} ·{" "}
                {formatMoney(booking.price)}
              </span>
            </div>
            <div>
              <MapPin />
              <span>
                <small>Location</small>
                {shop.address}
              </span>
            </div>
          </div>
          {booking.cancellable && !confirming ? (
            <Button variant="danger" onClick={() => setConfirming(true)}>
              Cancel appointment
            </Button>
          ) : null}
          {!booking.cancellable && booking.status === "BOOKED" ? (
            <p className="notice">
              Online cancellation has closed (2 hours before). Please call the
              shop: {shop.phone}
            </p>
          ) : null}
          {confirming ? (
            <div className="confirm-box">
              <Heading level={3}>Release this time?</Heading>
              <p>This can’t be undone.</p>
              <div>
                <Button variant="ghost" onClick={() => setConfirming(false)}>
                  Keep booking
                </Button>
                <Button variant="danger" loading={loading} onClick={cancel}>
                  <Check /> Yes, cancel
                </Button>
              </div>
            </div>
          ) : null}
          {error ? <p className="form-error">{error}</p> : null}
          <Button
            variant="ghost"
            onClick={() => {
              setBooking(null)
              setConfirming(false)
            }}
          >
            Find another booking
          </Button>
          <AppLink to="/" variant="secondary">
            Back to home
          </AppLink>
        </div>
      </main>
    )
  }
  return (
    <main className="result-page cancel-lookup-page">
      <div className="result-card cancel-lookup-card">
        <div className="cancel-code-icon">
          <KeyRound />
        </div>
        <Eyebrow>Manage your chair</Eyebrow>
        <Heading level={1}>Find your booking.</Heading>
        <p>
          Enter your booking code and the phone number you booked with. Codes
          start with FS- and aren’t case-sensitive. Online cancellation is free
          until 2 hours before the appointment.
        </p>
        <form
          noValidate
          onSubmit={lookup}
          onBlur={checks.onBlur}
          onChange={checks.onChange}
        >
          <Input
            label="Booking code"
            name="code"
            placeholder="FS-7K3QXM"
            autoComplete="off"
            required
            maxLength={limits["lookup.code"]}
            value={code}
            error={checks.errorFor("code")}
            onChange={(event) => setCode(event.target.value)}
          />
          <Input
            label="Phone number"
            name="phone"
            type="tel"
            placeholder="0888 123 456"
            autoComplete="tel"
            required
            maxLength={limits["lookup.phone"]}
            value={phone}
            error={checks.errorFor("phone")}
            onChange={(event) => setPhone(event.target.value)}
          />
          {error ? (
            <p className="form-error">
              <ShieldAlert /> {error}
            </p>
          ) : null}
          <Button type="submit" loading={loading}>
            Open booking
          </Button>
        </form>
        <AppLink to="/" variant="ghost">
          Back to Tattoo Studio
        </AppLink>
      </div>
    </main>
  )
}

export default CancellationLookup
