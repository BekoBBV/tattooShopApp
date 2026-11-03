import { useState, type FormEvent } from "react"
import { Scissors } from "lucide-react"
import { useLocation, useNavigate } from "react-router"
import { SprayDisc } from "../../components/brand"
import { AppLink, Button, Eyebrow, Heading, Input } from "../../components/ui"
import { api, saveSession } from "../../lib/api"
import { limits } from "../../lib/limits"
import { errorText } from "../../lib/useLoad"
import type { LoginResponse } from "../../lib/types"
import { accents } from "../../features/staff/staffUtils"
import { Feedback } from "../../features/staff/StaffUi"
import { usePageTitle } from "../../lib/usePageTitle"

/** Staff sign-in (/staff/login): one page for artists and the admin. */
export function StaffLogin() {
  usePageTitle("Staff sign-in")
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const expired = (location.state as { expired?: boolean } | null)?.expired
  async function submit(event: FormEvent) {
    event.preventDefault()
    setLoading(true)
    setError("")
    try {
      const result = await api<LoginResponse>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      })
      if (result.user.role === "USER") {
        setError("That's a customer account. Customers sign in on the main site.")
        return
      }
      saveSession(result)
      navigate(result.user.role === "ADMIN" ? "/admin" : "/staff", {
        replace: true,
      })
    } catch (reason) {
      setError(errorText(reason))
    } finally {
      setLoading(false)
    }
  }
  return (
    <main className="login-page">
      <div className="login-art" aria-hidden="true">
        <div className="login-tag">
          STAFF
          <br />
          <span>ONLY</span>
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
          <Eyebrow>Staff deck</Eyebrow>
          <Heading level={1}>Back in the chair.</Heading>
          <p>
            Artists see their own appointments and hours. The shop owner signs
            in as admin to run the whole shop.
          </p>
        </div>
        <form onSubmit={submit}>
          {expired && !error ? (
            <Feedback notice="Your session ended. Please sign in again." />
          ) : null}
          <Input
            label="Username"
            autoComplete="username"
            required
            maxLength={limits["login.username"]}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
          <Input
            label="Password"
            type="password"
            autoComplete="current-password"
            required
            maxLength={limits["login.password"]}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Feedback error={error} />
          <Button type="submit" loading={loading}>
            Sign in <Scissors />
          </Button>
        </form>
        <small>
          Staff only. Ask the shop owner for your login. Customers:{" "}
          <AppLink to="/account/sign-in">sign in here</AppLink>.
        </small>
      </section>
    </main>
  )
}
