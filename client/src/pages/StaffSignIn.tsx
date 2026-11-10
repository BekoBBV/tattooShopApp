import { useState, type FormEvent } from "react"
import { Link, Navigate, useNavigate } from "react-router"
import { FormError, TextField } from "../components/Studio"
import { api, saveSession, staffSignedIn } from "../lib/api"
import { limits } from "../lib/limits"
import type { LoginResponse } from "../lib/types"
import { usePageTitle } from "../lib/usePageTitle"
import { useFieldChecks } from "../lib/validation"

/** Staff sign-in for artists and the admin: a page of its own, without the site's header and footer. */
export default function StaffSignIn() {
  usePageTitle("Staff sign-in")
  const navigate = useNavigate()
  const [values, setValues] = useState({ username: "", password: "" })
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)
  const errors: Record<string, string> = {}
  if (!values.username.trim()) errors.username = "Enter your username."
  if (!values.password) errors.password = "Enter your password."
  const checks = useFieldChecks(errors)

  if (staffSignedIn()) return <Navigate to="/staff" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setBusy(true)
    setProblem("")
    try {
      const session = await api<LoginResponse>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: values.username.trim(), password: values.password }),
      })
      if (session.user.role === "USER") {
        setProblem("This is a customer account. Customers sign in under Account on the studio site.")
        setBusy(false)
        return
      }
      saveSession(session)
      navigate("/staff", { replace: true })
    } catch (reason) {
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
      setBusy(false)
    }
  }

  return (
    <main className="staff-sign-in-page">
      <div className="staff-sign-in-card">
        <Link to="/" className="lockup" aria-label="Blade and Brush home">
          <img src="/images/seal-bb.png" alt="" width="48" height="48" />
          <span className="wordmark">Blade &amp; Brush</span>
        </Link>
        <div>
          <h1>Staff sign-in</h1>
          <p>For Blade &amp; Brush artists and studio staff.</p>
        </div>
        <form className="slip on-paper" onSubmit={submit} onChange={checks.onChange} onBlur={checks.onBlur} noValidate>
          <FormError>{problem}</FormError>
          <TextField label="Username" name="username" autoComplete="username" autoCapitalize="none" spellCheck={false}
            maxLength={limits["login.username"]} value={values.username}
            onChange={(e) => setValues({ ...values, username: e.target.value })} error={checks.errorFor("username")} />
          <TextField label="Password" name="password" type="password" autoComplete="current-password"
            maxLength={limits["login.password"]} value={values.password}
            onChange={(e) => setValues({ ...values, password: e.target.value })} error={checks.errorFor("password")} />
          <button className="button button-primary" type="submit" disabled={busy}>
            {busy ? "Signing in..." : "Sign in"}
          </button>
        </form>
        <Link to="/">Back to studio site</Link>
      </div>
    </main>
  )
}
