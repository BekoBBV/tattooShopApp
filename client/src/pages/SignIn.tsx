import { useState, type FormEvent } from "react"
import { Link, useNavigate, useSearchParams } from "react-router"
import { PageIntro } from "../components/Layout"
import { FormError, PasswordRules, TextField } from "../components/Studio"
import { api, ApiError } from "../lib/api"
import { setCustomer } from "../lib/customer"
import { parseTypedDate, todayInSofia } from "../lib/format"
import { limits } from "../lib/limits"
import type { LoginResponse } from "../lib/types"
import { usePageTitle } from "../lib/usePageTitle"
import { checkRegister, checkSignIn, passwordRules, useFieldChecks } from "../lib/validation"

/** Only paths on this site, so a link can't send people elsewhere after signing in. */
const safeNext = (next: string | null) => (next && next.startsWith("/") && !next.startsWith("//") ? next : "/account")

export function SignIn() {
  usePageTitle("Sign in")
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [values, setValues] = useState({ email: "", password: "" })
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)
  const checks = useFieldChecks(checkSignIn(values))

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    try {
      const session = await api<LoginResponse>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: values.email.trim(), password: values.password }),
      })
      if (session.user.role !== "USER") {
        setProblem("This is a staff login. Staff sign in on the staff page.")
        return
      }
      setCustomer(session)
      navigate(safeNext(params.get("next")))
    } catch (reason) {
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <PageIntro title="Sign in" stroke={false}>
        <p>See your bookings, consultations and account details.</p>
      </PageIntro>
      <section className="wrap auth-shell">
        <form className="slip on-paper auth-form" onSubmit={submit} onChange={checks.onChange} onBlur={checks.onBlur} noValidate>
          <FormError>{problem}</FormError>
          <TextField label="Email" name="email" type="email" autoComplete="email" maxLength={limits["login.username"]}
            value={values.email} onChange={(e) => setValues({ ...values, email: e.target.value })} error={checks.errorFor("email")} />
          <TextField label="Password" name="password" type="password" autoComplete="current-password" maxLength={limits["login.password"]}
            value={values.password} onChange={(e) => setValues({ ...values, password: e.target.value })} error={checks.errorFor("password")} />
          <button className="button button-primary" type="submit" disabled={busy}>
            {busy ? "Signing in..." : "Sign in"}
          </button>
          <div className="auth-links">
            <Link to={`/account/register${params.get("next") ? `?next=${encodeURIComponent(params.get("next")!)}` : ""}`}>Create an account</Link>
            <Link to="/manage">Manage a booking without an account</Link>
          </div>
        </form>
      </section>
    </main>
  )
}

export function Register() {
  usePageTitle("Create an account")
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [values, setValues] = useState({ name: "", email: "", phone: "", dateOfBirth: "", password: "" })
  const [problem, setProblem] = useState("")
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const checks = useFieldChecks(checkRegister(values, todayInSofia()), serverErrors)
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    setServerErrors({})
    try {
      const session = await api<LoginResponse>("/auth/register", {
        method: "POST",
        body: JSON.stringify({ ...values, name: values.name.trim(), email: values.email.trim(), dateOfBirth: parseTypedDate(values.dateOfBirth) }),
      })
      setCustomer(session)
      navigate(safeNext(params.get("next")))
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setServerErrors(reason.errors)
      if (reason instanceof ApiError && reason.code === "UNDER_AGE") setServerErrors({ dateOfBirth: reason.message })
      else setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <PageIntro title="Create an account" stroke={false}>
        <p>Keep your appointments, references and studio messages together.</p>
      </PageIntro>
      <section className="wrap auth-shell">
        <form className="slip on-paper auth-form is-wide" onSubmit={submit} onChange={checks.onChange} onBlur={checks.onBlur} noValidate>
          <FormError>{problem}</FormError>
          <TextField label="Full name" name="name" autoComplete="name" maxLength={limits["account.name"]} value={values.name} onChange={set("name")} error={checks.errorFor("name")} />
          <TextField label="Email" name="email" type="email" autoComplete="email" maxLength={limits["account.email"]} value={values.email} onChange={set("email")} error={checks.errorFor("email")} />
          <TextField label="Phone" name="phone" type="tel" autoComplete="tel" maxLength={limits["account.phone"]} value={values.phone} onChange={set("phone")} error={checks.errorFor("phone")} />
          <TextField label="Date of birth" name="dateOfBirth" inputMode="numeric" placeholder="dd.mm.yyyy" autoComplete="bday" value={values.dateOfBirth} onChange={set("dateOfBirth")} error={checks.errorFor("dateOfBirth")} />
          <TextField label="Password" name="password" type="password" autoComplete="new-password" maxLength={limits["account.password"]} value={values.password} onChange={set("password")} error={checks.errorFor("password")} />
          <PasswordRules rules={passwordRules(values.password)} />
          <button className="button button-primary" type="submit" disabled={busy}>
            {busy ? "Creating..." : "Create account"}
          </button>
          <p className="auth-switch">
            Already have an account? <Link to={`/account/sign-in${params.get("next") ? `?next=${encodeURIComponent(params.get("next")!)}` : ""}`}>Sign in</Link>
          </p>
        </form>
      </section>
    </main>
  )
}
