import { AppLink, Heading } from "../components/ui"
import { usePageTitle } from "../lib/usePageTitle"

export function NotFound() {
  usePageTitle("Page not found")
  return (
    <main className="result-page">
      <div className="result-card">
        <span className="error-code">404</span>
        <Heading level={1}>Wrong street.</Heading>
        <p>That page slipped out of the rotation.</p>
        <AppLink to="/" variant="primary">
          Back to the shop
        </AppLink>
      </div>
    </main>
  )
}
