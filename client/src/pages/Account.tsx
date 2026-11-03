import { useState, type FormEvent, type ReactNode } from "react"
import { ArrowRight, LogOut, Scissors } from "lucide-react"
import { Navigate, useLocation, useNavigate } from "react-router"
import { SiteFooter, SiteHeader, SprayDisc } from "../components/brand"
import { ListToolbar, Pager } from "../components/ListControls"
import {
  AppLink,
  Button,
  Eyebrow,
  Heading,
  Input,
  Select,
  StatusBadge,
} from "../components/ui"
import { Feedback } from "../features/staff/StaffUi"
import { accents, formatDay, useNow } from "../features/staff/staffUtils"
import { ApiError, api } from "../lib/api"
import { setCustomer, useCustomer } from "../lib/customer"
import { formatMoney, shop, telHref } from "../lib/data"
import { useListQuery, type Page } from "../lib/paging"
import type { Account, Booking, LoginResponse } from "../lib/types"
import { errorText, useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"
import {
  checkRegister,
  checkSignIn,
  useFieldChecks,
  type FieldErrors,
} from "../lib/validation"
import { limits } from "../lib/limits"

/** Where to go after signing in: back to the booking form if the customer came from there. */
function useBackTo() {
  const state = useLocation().state as { back?: string } | null
  return state?.back?.startsWith("/") ? state.back : "/account"
}

/** The sign-in / sign-up frame: spray-can art on the left, the form on the right. */
function AccountFrame({
  tag,
  title,
  intro,
  children,
  note,
}: {
  tag: [string, string]
  title: string
  intro: string
  children: ReactNode
  note: ReactNode
}) {
  return (
    <main className="login-page">
      <div className="login-art" aria-hidden="true">
        <div className="login-tag">
          {tag[0]}
          <br />
          <span>{tag[1]}</span>
        </div>
        {accents.map((accent) => (
          <SprayDisc key={accent} accent={accent} />
        ))}
      </div>
      <section className="login-panel">
        <AppLink to="/" className="brand">
          <Scissors /> TATTOO <span>STUDIO</span>
        </AppLink>
        <div>
          <Eyebrow>Your account</Eyebrow>
          <Heading level={1}>{title}</Heading>
          <p>{intro}</p>
        </div>
        {children}
        <small>{note}</small>
      </section>
    </main>
  )
}

/** Signs a customer in after sign-in or sign-up; staff logins belong on the staff page. */
function useFinishSignIn() {
  const navigate = useNavigate()
  const back = useBackTo()
  return (result: LoginResponse) => {
    if (result.user.role !== "USER") return false
    setCustomer(result)
    navigate(back, { replace: true })
    return true
  }
}

/** /account/sign-in */
export function AccountSignIn() {
  usePageTitle("Sign in")
  const finish = useFinishSignIn()
  const back = useBackTo()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<ReactNode>("")
  const [loading, setLoading] = useState(false)
  const checks = useFieldChecks(checkSignIn({ email, password }))
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setLoading(true)
    setError("")
    try {
      const result = await api<LoginResponse>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: email, password }),
      })
      if (!finish(result))
        setError(
          <>
            That’s a staff login. Please use the{" "}
            <AppLink to="/staff/login">staff sign-in</AppLink>.
          </>,
        )
    } catch (reason) {
      setError(errorText(reason))
    } finally {
      setLoading(false)
    }
  }
  return (
    <AccountFrame
      tag={["CREW", "CLUB"]}
      title="Welcome back."
      intro="Sign in to book faster and see all your bookings in one place. No account? You can still book as a guest."
      note={
        <>
          New here?{" "}
          <AppLink to="/account/register" state={{ back }}>
            Create an account
          </AppLink>{" "}
          · Artists and the owner use the{" "}
          <AppLink to="/staff/login">staff login</AppLink>.
        </>
      }
    >
      <form
        noValidate
        onSubmit={submit}
        onBlur={checks.onBlur}
        onChange={checks.onChange}
      >
        <Input
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          required
          maxLength={limits["account.email"]}
          value={email}
          error={checks.errorFor("email")}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Input
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          maxLength={limits["login.password"]}
          value={password}
          error={checks.errorFor("password")}
          onChange={(e) => setPassword(e.target.value)}
        />
        {error ? <p className="form-error">{error}</p> : null}
        <Button type="submit" loading={loading}>
          Sign in
        </Button>
      </form>
    </AccountFrame>
  )
}

/** /account/register */
export function AccountRegister() {
  usePageTitle("Create an account")
  const finish = useFinishSignIn()
  const back = useBackTo()
  const [form, setForm] = useState({ name: "", phone: "", email: "", password: "" })
  const [error, setError] = useState<ReactNode>("")
  const [serverErrors, setServerErrors] = useState<FieldErrors>({})
  const [loading, setLoading] = useState(false)
  const checks = useFieldChecks(checkRegister(form), serverErrors)
  const field = (name: keyof typeof form) => ({
    name,
    value: form[name],
    error: checks.errorFor(name),
    onChange: (e: { target: { value: string } }) =>
      setForm((f) => ({ ...f, [name]: e.target.value })),
  })
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setLoading(true)
    setError("")
    setServerErrors({})
    try {
      finish(
        await api<LoginResponse>("/auth/register", {
          method: "POST",
          body: JSON.stringify(form),
        }),
      )
    } catch (reason) {
      if (reason instanceof ApiError) setServerErrors(reason.errors || {})
      setError(
        reason instanceof ApiError && reason.code === "CONFLICT" ? (
          <>
            {reason.message}{" "}
            <AppLink to="/account/sign-in" state={{ back }}>
              Sign in
            </AppLink>
          </>
        ) : (
          errorText(reason)
        ),
      )
    } finally {
      setLoading(false)
    }
  }
  return (
    <AccountFrame
      tag={["FRESH", "FACES"]}
      title="Join the crew."
      intro="One account for all your bookings. We only use your phone for booking reminders."
      note={
        <>
          Already have an account?{" "}
          <AppLink to="/account/sign-in" state={{ back }}>
            Sign in
          </AppLink>
        </>
      }
    >
      <form
        noValidate
        onSubmit={submit}
        onBlur={checks.onBlur}
        onChange={checks.onChange}
      >
        <Input label="Name" autoComplete="name" required maxLength={limits["account.name"]} {...field("name")} />
        <Input
          label="Phone"
          type="tel"
          autoComplete="tel"
          placeholder="0888 123 456"
          required
          maxLength={limits["account.phone"]}
          {...field("phone")}
        />
        <Input
          label="Email"
          type="email"
          autoComplete="email"
          required
          maxLength={limits["account.email"]}
          {...field("email")}
        />
        <Input
          label="Password (8+ characters)"
          type="password"
          autoComplete="new-password"
          required
          minLength={limits["account.passwordMin"]}
          maxLength={limits["account.password"]}
          {...field("password")}
        />
        {error ? <p className="form-error">{error}</p> : null}
        <Button type="submit" loading={loading}>
          Create account
        </Button>
      </form>
    </AccountFrame>
  )
}

/** Change password and delete the account, each confirmed with the current password. */
function AccountSettings({ token, onDeleted }: { token: string; onDeleted: () => void }) {
  const [current, setCurrent] = useState("")
  const [next, setNext] = useState("")
  const [deletePassword, setDeletePassword] = useState("")
  const [busy, setBusy] = useState("")
  const [feedback, setFeedback] = useState({ error: "", notice: "" })
  async function run(key: string, action: () => Promise<void>) {
    setBusy(key)
    setFeedback({ error: "", notice: "" })
    try {
      await action()
    } catch (reason) {
      setFeedback({ error: errorText(reason), notice: "" })
    } finally {
      setBusy("")
    }
  }
  const changePassword = (event: FormEvent) => {
    event.preventDefault()
    run("password", async () => {
      // The answer is a fresh sign-in: other devices are signed out, this one stays signed in
      setCustomer(
        await api<LoginResponse>("/account/password", {
          method: "PUT",
          token,
          body: JSON.stringify({ currentPassword: current, newPassword: next }),
        }),
      )
      setCurrent("")
      setNext("")
      setFeedback({ error: "", notice: "Password changed. Other devices have to sign in again." })
    })
  }
  const deleteAccount = (event: FormEvent) => {
    event.preventDefault()
    if (!window.confirm("Delete your account for good? Your bookings stay with the shop; upcoming ones still work with their code and phone number.")) return
    run("delete", async () => {
      await api("/account/delete", { method: "POST", token, body: JSON.stringify({ password: deletePassword }) })
      onDeleted()
    })
  }
  return (
    <details className="account-settings">
      <summary>Account settings</summary>
      <Feedback error={feedback.error} notice={feedback.notice} />
      <div className="account-settings-forms">
        <form className="details-form" onSubmit={changePassword}>
          <Heading level={3}>Change password</Heading>
          <Input
            label="Current password"
            type="password"
            autoComplete="current-password"
            required
            maxLength={limits["account.password"]}
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
          <Input
            label={`New password (${limits["account.passwordMin"]}+ characters)`}
            type="password"
            autoComplete="new-password"
            required
            minLength={limits["account.passwordMin"]}
            maxLength={limits["account.password"]}
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
          <Button type="submit" variant="secondary" loading={busy === "password"}>
            Change password
          </Button>
        </form>
        <form className="details-form" onSubmit={deleteAccount}>
          <Heading level={3}>Delete my account</Heading>
          <p>Removes your name, email, phone and password. Your bookings stay with the shop for its records.</p>
          <Input
            label="Password"
            type="password"
            autoComplete="current-password"
            required
            maxLength={limits["account.password"]}
            value={deletePassword}
            onChange={(e) => setDeletePassword(e.target.value)}
          />
          <Button type="submit" variant="danger" loading={busy === "delete"}>
            Delete my account
          </Button>
        </form>
      </div>
    </details>
  )
}

const whenTabs: [string, string][] = [
  ["UPCOMING", "Upcoming"],
  ["PAST", "Past & cancelled"],
  ["ALL", "All"],
]

/** /account: the signed-in customer's bookings, searchable, sortable and paged. */
export function MyBookings() {
  usePageTitle("My bookings")
  const customer = useCustomer()
  const navigate = useNavigate()
  const token = customer?.token
  const account = useLoad<Account>(token ? "/account" : null, token)
  const list = useListQuery({ sort: "date,asc", filters: { when: "UPCOMING", status: "" } })
  const load = useLoad<Page<Booking>>(token ? `/account/bookings?${list.query}` : null, token)
  const [busy, setBusy] = useState("")
  const [feedback, setFeedback] = useState({ error: "", notice: "" })
  const now = useNow()
  // Signing out: go home, not to the sign-in page this guard would otherwise show
  const [leaving, setLeaving] = useState(false)
  if (!customer && leaving) return null
  if (!customer) return <Navigate to="/account/sign-in" replace state={{ back: "/account" }} />

  function signOut() {
    setLeaving(true)
    setCustomer(null)
    navigate("/")
  }
  const items = load.data?.items
  const isUpcoming = (b: Booking) => b.status === "BOOKED" && new Date(b.endsAt).getTime() > now
  const filtered = !!(list.q || list.filters.status)

  async function cancel(booking: Booking) {
    if (!window.confirm(`Cancel ${booking.serviceName} on ${formatDay(booking.date)} at ${booking.time}?`)) return
    setBusy(booking.code)
    setFeedback({ error: "", notice: "" })
    try {
      await api(`/account/bookings/${encodeURIComponent(booking.code)}/cancel`, { method: "POST", token })
      setFeedback({ error: "", notice: "Cancelled. The time is free for someone else now." })
      load.reload()
    } catch (reason) {
      setFeedback({ error: errorText(reason), notice: "" })
    } finally {
      setBusy("")
    }
  }

  return (
    <main className="account-page">
      <SiteHeader />
      <div className="dashboard-content">
        <div className="dashboard-heading">
          <div>
            <Eyebrow>Your account</Eyebrow>
            <Heading level={1}>My bookings.</Heading>
            <p>
              Signed in as {account.data?.name ?? customer.user.name}
              {account.data ? ` · ${account.data.email} · ${account.data.phone}` : ""}
            </p>
          </div>
          <div className="row-actions">
            <AppLink to="/book/service" variant="primary">
              Book a chair <ArrowRight />
            </AppLink>
            <Button variant="secondary" onClick={signOut}>
              Sign out <LogOut />
            </Button>
          </div>
        </div>
        <div className="list-tabs" role="group" aria-label="Which bookings">
          {whenTabs.map(([value, label]) => (
            <Button
              key={value}
              variant="secondary"
              aria-pressed={list.filters.when === value}
              onClick={() => {
                list.setFilter("when", value)
                // Upcoming reads soonest first, the others latest first
                list.setSort(value === "UPCOMING" ? "date,asc" : "date,desc")
              }}
            >
              {label}
            </Button>
          ))}
        </div>
        <ListToolbar
          list={list}
          searchLabel="Search my bookings"
          placeholder="Service or booking code…"
          sorts={[
            ["date,asc", "Date: earliest first"],
            ["date,desc", "Date: latest first"],
            ["service,asc", "Service A–Z"],
            ["price,desc", "Price: high to low"],
            ["price,asc", "Price: low to high"],
          ]}
        >
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
        </ListToolbar>
        <Feedback error={feedback.error || load.error} notice={feedback.notice} />
        {!items ? (
          <p className="account-empty">Loading your bookings…</p>
        ) : !items.length ? (
          <p className="account-empty">
            {filtered ? (
              "No bookings match this search."
            ) : list.filters.when === "UPCOMING" ? (
              <>
                Nothing booked yet. <AppLink to="/book/service">Book a chair</AppLink> and it shows up here.
              </>
            ) : (
              "Your past and cancelled bookings will show up here."
            )}
          </p>
        ) : (
          <div className="booking-table account-bookings">
            {items.map((b) => (
              <article key={b.code} className={b.status === "CANCELLED" ? "is-cancelled" : ""}>
                <time dateTime={b.startsAt}>
                  {formatDay(b.date)} · {b.time}
                </time>
                <div>
                  <Heading level={3}>{b.serviceName}</Heading>
                  <p>
                    with {b.artistName ?? "the shop"} · {formatMoney(b.price)} · code {b.code}
                  </p>
                </div>
                <StatusBadge status={b.status} />
                <div className="row-actions">
                  {b.status === "BOOKED" && b.cancellable ? (
                    <Button variant="ghost" loading={busy === b.code} disabled={!!busy} onClick={() => cancel(b)}>
                      Cancel
                    </Button>
                  ) : null}
                  {b.status === "BOOKED" && !b.cancellable && isUpcoming(b) ? (
                    <small>Call to cancel</small>
                  ) : null}
                  {b.status === "DONE" ? (
                    <AppLink to="/book/service" variant="secondary">
                      Book again
                    </AppLink>
                  ) : null}
                </div>
              </article>
            ))}
          </div>
        )}
        <Pager data={load.data} list={list} noun="booking" />
        <AccountSettings token={token!} onDeleted={signOut} />
        <p className="account-note">
          You can cancel up to 2 hours before your appointment. Later than that? Call us on{" "}
          <a href={telHref(shop.phone)}>{shop.phone}</a>. Bookings you made as a
          guest are managed with their code under <AppLink to="/cancel">Manage booking</AppLink>.
        </p>
      </div>
      <SiteFooter />
    </main>
  )
}
