import { Link } from "react-router"
import { KatanaDivider, PageIntro } from "../components/Layout"
import { usePageTitle } from "../lib/usePageTitle"

export default function NotFound() {
  usePageTitle("Page not found")
  return (
    <main>
      <PageIntro title="This page doesn't exist" stroke={false}>
        <p>The link may be old or mistyped.</p>
      </PageIntro>
      <section className="wrap not-found-shell">
        <KatanaDivider />
        <Link className="button button-primary" to="/">
          Back to home
        </Link>
      </section>
    </main>
  )
}
