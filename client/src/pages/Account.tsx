import { LockKeyhole, LogOut, Pencil, Trash2 } from "lucide-react"
import { useState, type FormEvent } from "react"
import { Link, Navigate, useNavigate } from "react-router"
import { CancelDialog, ReviewDialog } from "../components/BookingDialogs"
import { PageIntro } from "../components/Layout"
import { ArtistSeal, Dialog, FormError, PasswordRules, Stamp, TextField } from "../components/Studio"
import { api, ApiError } from "../lib/api"
import { cancelledNote, kindOf, statusOf, titleOf } from "../lib/bookingText"
import { setCustomer, useCustomer } from "../lib/customer"
import { cardDate, euros, placementLabel, plainDate } from "../lib/format"
import { limits } from "../lib/limits"
import type { Account as AccountData, Booking, LoginResponse, Page, PaymentStart } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"
import { checkAccountDetails, passwordRules, useFieldChecks } from "../lib/validation"

type DialogState =
  | { kind: "cancel" | "review"; booking: Booking }
  | { kind: "details" }
  | { kind: "password" }
  | { kind: "delete" }

/** My account: upcoming bookings, consultations, past appointments and the account details. */
export default function Account() {
  usePageTitle("My account")
  const customer = useCustomer()
  const navigate = useNavigate()
  const token = customer?.token
  const account = useLoad<AccountData>(customer ? "/account" : null, token)
  const upcoming = useLoad<Page<Booking>>(customer ? "/account/bookings?when=UPCOMING&size=50" : null, token)
  const past = useLoad<Page<Booking>>(customer ? "/account/bookings?when=PAST&size=50" : null, token)
  const consultations = useLoad<Page<Booking>>(customer ? "/account/bookings?when=ALL&type=CONSULTATION&size=50" : null, token)
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")
  const [paying, setPaying] = useState<string | null>(null)

  if (!customer) return <Navigate to="/account/sign-in?next=/account" replace />

  const reload = () => {
    upcoming.reload()
    past.reload()
    consultations.reload()
  }

  async function pay(b: Booking) {
    setPaying(b.code)
    setProblem("")
    try {
      const start = await api<PaymentStart>(`/account/bookings/${encodeURIComponent(b.code)}/deposit`, { method: "POST", token })
      window.location.assign(start.checkoutUrl)
    } catch (reason) {
      setPaying(null)
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
      reload()
    }
  }

  const card = (b: Booking, isPast: boolean) => {
    const status = statusOf(b)
    return (
      <article key={b.code} className="slip on-paper account-card">
        <div className="account-card-topline">
          <p>{isPast && b.type === "SESSION" ? "Custom session" : kindOf(b)}</p>
          <Stamp tone={status.tone}>{status.label}</Stamp>
        </div>
        <div className="account-card-title">
          <div>
            <h3>{titleOf(b)}</h3>
            <p>
              {isPast ? plainDate(b.date) : cardDate(b.date)}, {b.time} to {b.endTime}
            </p>
          </div>
          {b.artistName && <ArtistSeal name={b.artistName} px={48} />}
        </div>
        <dl className="slip-list">
          <div>
            <dt>Artist</dt>
            <dd>{b.artistName}</dd>
          </div>
          {b.price !== null && (
            <div>
              <dt>Price</dt>
              <dd>{euros(b.price)}</dd>
            </div>
          )}
          {!isPast && b.deposit.amount !== null && (
            <div>
              <dt>Deposit</dt>
              <dd>{euros(b.deposit.amount)}</dd>
            </div>
          )}
        </dl>
        {(b.canPayDeposit || b.canReview || b.cancellable) && (
          <div className="account-card-actions">
            {b.canPayDeposit && (
              <button className="button button-primary" type="button" onClick={() => void pay(b)} disabled={paying !== null}>
                {paying === b.code ? "Taking you to Revolut to pay..." : `Pay ${euros(b.deposit.amount)} deposit`}
              </button>
            )}
            {b.canReview && (
              <button className="button button-secondary" type="button" onClick={() => setDialog({ kind: "review", booking: b })}>
                Leave a review
              </button>
            )}
            {b.cancellable && (
              <button className="button button-secondary" type="button" onClick={() => setDialog({ kind: "cancel", booking: b })}>
                Cancel booking
              </button>
            )}
          </div>
        )}
      </article>
    )
  }

  const upcomingItems = upcoming.data?.items ?? []
  const pastItems = past.data?.items ?? []
  const consultationItems = consultations.data?.items ?? []
  const loadError = upcoming.error || past.error || consultations.error || account.error

  return (
    <main>
      <PageIntro title="My account" compact>
        <p>Your appointments, consultation ideas and account details.</p>
      </PageIntro>
      <div className="wrap account-layout">
        <nav className="account-section-nav" aria-label="Account sections">
          <a href="#upcoming">Upcoming</a>
          <a href="#consultations">Consultations</a>
          <a href="#past">Past</a>
          <a href="#account-details">Account details</a>
        </nav>
        <div>
          <FormError>{problem || loadError}</FormError>
          {note && (
            <p className="form-sent" role="status">
              {note}
            </p>
          )}
          <section className="account-section" id="upcoming">
            <div className="account-section-heading">
              <h2>Upcoming</h2>
              <span>{upcomingItems.length}</span>
            </div>
            {upcoming.data && upcomingItems.length === 0 ? (
              <div>
                <p className="state-note">No bookings yet. Book a flash design or a free consultation.</p>
                <div className="artist-actions">
                  <Link className="button button-primary" to="/flash">
                    Book a flash design
                  </Link>
                  <Link className="button button-secondary" to="/book/choose?type=consultation">
                    Book a free consultation
                  </Link>
                </div>
              </div>
            ) : (
              <div className="account-card-grid">{upcomingItems.map((b) => card(b, false))}</div>
            )}
          </section>
          <section className="account-section" id="consultations">
            <div className="account-section-heading">
              <h2>Consultations</h2>
              <span>{consultationItems.length}</span>
            </div>
            {consultations.data && consultationItems.length === 0 && <p className="state-note">No consultations yet.</p>}
            {consultationItems.map((b) => (
              <article key={b.code} className="slip on-paper consultation-card">
                <div>
                  <h3>{b.consultation?.placement ? `${placementLabel(b.consultation.placement)} idea` : "Your idea"}</h3>
                  <p className="consultation-meta">
                    Consultation with {b.artistName}, {cardDate(b.date)}
                  </p>
                  <p>{b.consultation?.idea}</p>
                </div>
                {b.consultation && b.consultation.photos.length > 0 && (
                  <div className="consultation-thumbnails">
                    {b.consultation.photos.map((photo, i) => (
                      <figure key={photo.id}>
                        <img src={photo.thumbUrl} alt="" />
                        <figcaption>Reference {i + 1}</figcaption>
                      </figure>
                    ))}
                  </div>
                )}
              </article>
            ))}
          </section>
          <section className="account-section" id="past">
            <div className="account-section-heading">
              <h2>Past</h2>
              <span>{pastItems.length}</span>
            </div>
            {past.data && pastItems.length === 0 && <p className="state-note">Nothing here yet.</p>}
            <div className="account-card-grid">{pastItems.map((b) => card(b, true))}</div>
          </section>
          <section className="account-section" id="account-details">
            <div className="account-section-heading">
              <h2>Account details</h2>
            </div>
            <div className="slip on-paper details-card">
              {account.data && (
                <>
                  <div className="detail-row">
                    <span>Name</span>
                    <strong>{account.data.name}</strong>
                  </div>
                  <div className="detail-row">
                    <span>Phone</span>
                    <strong>{account.data.phone}</strong>
                  </div>
                  <div className="detail-row">
                    <span>Email</span>
                    <strong>{account.data.email}</strong>
                  </div>
                </>
              )}
              <div className="detail-actions">
                <button className="button button-secondary" type="button" disabled={!account.data} onClick={() => setDialog({ kind: "details" })}>
                  <Pencil className="icon" size={18} aria-hidden="true" /> Edit details
                </button>
                <button className="button button-secondary" type="button" onClick={() => setDialog({ kind: "password" })}>
                  <LockKeyhole className="icon" size={18} aria-hidden="true" /> Change password
                </button>
                <button
                  className="button button-secondary"
                  type="button"
                  onClick={() => {
                    setCustomer(null)
                    navigate("/")
                  }}
                >
                  <LogOut className="icon" size={18} aria-hidden="true" /> Sign out
                </button>
              </div>
              <div className="delete-account">
                <div>
                  <h3>Delete account</h3>
                  <p>Delete your profile details. Bookings still work with their code.</p>
                </div>
                <button className="destructive-link" type="button" onClick={() => setDialog({ kind: "delete" })}>
                  <Trash2 className="icon" size={18} aria-hidden="true" /> Delete account
                </button>
              </div>
            </div>
          </section>
        </div>
      </div>
      {dialog?.kind === "cancel" && (
        <CancelDialog
          booking={dialog.booking}
          onClose={() => setDialog(null)}
          onCancel={async () => {
            const b = await api<Booking>(`/account/bookings/${encodeURIComponent(dialog.booking.code)}/cancel`, { method: "POST", token })
            setNote(cancelledNote(b))
            setDialog(null)
            reload()
          }}
        />
      )}
      {dialog?.kind === "review" && (
        <ReviewDialog
          booking={dialog.booking}
          onClose={() => setDialog(null)}
          onSend={async (review) => {
            await api(`/account/bookings/${encodeURIComponent(dialog.booking.code)}/review`, {
              method: "POST",
              body: JSON.stringify(review),
              token,
            })
            setNote("Thank you for the review. It appears on the artist's page once the studio has checked it.")
            setDialog(null)
            reload()
          }}
        />
      )}
      {dialog?.kind === "details" && account.data && (
        <DetailsDialog
          token={token!}
          account={account.data}
          onClose={() => setDialog(null)}
          onDone={(emailChanged) => {
            setNote(emailChanged ? "Details saved. Sign in with your new email from now on." : "Details saved.")
            setDialog(null)
            account.reload()
          }}
        />
      )}
      {dialog?.kind === "password" && (
        <PasswordDialog
          token={token!}
          onClose={() => setDialog(null)}
          onDone={() => {
            setNote("Password changed. Other devices are signed out.")
            setDialog(null)
          }}
        />
      )}
      {dialog?.kind === "delete" && (
        <DeleteDialog
          token={token!}
          onClose={() => setDialog(null)}
          onDone={() => {
            setCustomer(null)
            navigate("/")
          }}
        />
      )}
    </main>
  )
}

/** Name, phone and email. The email is the sign-in, so the answer is a new sign-in. */
function DetailsDialog({
  token,
  account,
  onClose,
  onDone,
}: {
  token: string
  account: AccountData
  onClose: () => void
  onDone: (emailChanged: boolean) => void
}) {
  const [values, setValues] = useState({ name: account.name, phone: account.phone, email: account.email, password: "" })
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  // A new email is a new sign-in, so it asks for the password (the server checks it)
  const emailChanged = values.email.trim().toLowerCase() !== account.email.trim().toLowerCase()
  const checks = useFieldChecks(
    { ...checkAccountDetails(values), ...(emailChanged && !values.password ? { password: "Enter your password to change your email." } : {}) },
    serverErrors,
  )
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    setServerErrors({})
    try {
      const session = await api<LoginResponse>("/account", {
        method: "PUT",
        body: JSON.stringify({
          name: values.name.trim(),
          phone: values.phone.trim(),
          email: values.email.trim(),
          password: emailChanged ? values.password : null,
        }),
        token,
      })
      setCustomer(session)
      onDone(session.user.username !== account.email.trim().toLowerCase())
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setServerErrors(reason.errors)
      else if (reason instanceof ApiError && reason.code === "INVALID_PHONE") setServerErrors({ phone: reason.message })
      else if (reason instanceof ApiError && reason.status === 409) setServerErrors({ email: "Another account already uses this email." })
      else if (reason instanceof ApiError && reason.code === "VALIDATION_FAILED" && emailChanged) setServerErrors({ password: reason.message })
      else setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
      setBusy(false)
    }
  }

  return (
    <Dialog title="Edit details" titleId="details-title" onClose={onClose} as="form" onSubmit={submit}
      onChange={checks.onChange} onBlur={checks.onBlur} closeButton>
      <FormError>{problem}</FormError>
        <TextField label="Full name" name="name" autoComplete="name" maxLength={limits["account.name"]}
          value={values.name} onChange={set("name")} error={checks.errorFor("name")} />
        <TextField label="Phone" name="phone" type="tel" autoComplete="tel" maxLength={limits["account.phone"]}
          value={values.phone} onChange={set("phone")} error={checks.errorFor("phone")} />
        <TextField label="Email" name="email" type="email" autoComplete="email" maxLength={limits["account.email"]}
          value={values.email} onChange={set("email")} error={checks.errorFor("email")}
          helper="You sign in with this email." />
        {emailChanged && (
          <TextField label="Your password" name="password" type="password" autoComplete="current-password"
            maxLength={limits["login.password"]} value={values.password} onChange={set("password")} error={checks.errorFor("password")}
            helper="Needed to change your email." />
        )}
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Saving..." : "Save details"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>
          Cancel
        </button>
      </div>
    </Dialog>
  )
}

/** The current password, then a new one with the rules shown under it. */
function PasswordDialog({ token, onClose, onDone }: { token: string; onClose: () => void; onDone: () => void }) {
  const [current, setCurrent] = useState("")
  const [next, setNext] = useState("")
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  const rules = passwordRules(next)
  const errors: Record<string, string> = {}
  if (!current) errors.currentPassword = "Enter your current password."
  if (rules.some((r) => !r.met)) errors.newPassword = "Your new password needs all three things listed below it."
  const checks = useFieldChecks(errors)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    try {
      const session = await api<LoginResponse>("/account/password", {
        method: "PUT",
        body: JSON.stringify({ currentPassword: current, newPassword: next }),
        token,
      })
      setCustomer(session)
      onDone()
    } catch (reason) {
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
      setBusy(false)
    }
  }

  return (
    <Dialog title="Change password" titleId="password-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      <TextField label="Current password" name="currentPassword" type="password" autoComplete="current-password"
        maxLength={limits["account.password"]} value={current} onChange={(e) => setCurrent(e.target.value)}
        error={checks.errorFor("currentPassword")} />
      <TextField label="New password" name="newPassword" type="password" autoComplete="new-password"
        maxLength={limits["account.password"]} value={next} onChange={(e) => setNext(e.target.value)}
        error={checks.errorFor("newPassword")} />
      <PasswordRules rules={rules} />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Saving..." : "Change password"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>
          Cancel
        </button>
      </div>
    </Dialog>
  )
}

/** "Delete your account?", confirmed with the password. */
function DeleteDialog({ token, onClose, onDone }: { token: string; onClose: () => void; onDone: () => void }) {
  const [password, setPassword] = useState("")
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  const checks = useFieldChecks(password ? {} : { password: "Enter your password to confirm." })

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    try {
      await api("/account/delete", { method: "POST", body: JSON.stringify({ password }), token })
      onDone()
    } catch (reason) {
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
      setBusy(false)
    }
  }

  return (
    <Dialog title="Delete your account?" titleId="delete-account-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <p>This removes your profile. It does not cancel upcoming appointments: they still work with their code and phone number.</p>
      <FormError>{problem}</FormError>
      <TextField label="Password" name="password" type="password" autoComplete="current-password"
        maxLength={limits["account.password"]} value={password} onChange={(e) => setPassword(e.target.value)}
        error={checks.errorFor("password")} />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Deleting..." : "Delete account"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>
          Keep account
        </button>
      </div>
    </Dialog>
  )
}
