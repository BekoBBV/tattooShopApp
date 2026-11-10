import { Link } from "react-router"
import { PageIntro } from "../components/Layout"
import { usePageTitle } from "../lib/usePageTitle"

const STAGES = [
  {
    range: "Day 1",
    text: "Keep the wrap on for 2 to 4 hours, then wash gently with lukewarm water and unscented soap, pat dry, and apply a thin layer of the aftercare cream.",
  },
  { range: "Days 2 to 3", text: "Wash twice a day, apply a thin layer of cream, and wear loose clean clothes over it." },
  { range: "Days 4 to 14", text: "Peeling and itching are normal. Don't scratch or pick. No swimming, sauna or sunbathing." },
  { range: "Days 15 to 30", text: "The skin settles. Keep it moisturized and out of strong sun." },
  { range: "After a month", text: "Always use high SPF sunscreen on the tattoo to keep the colors sharp." },
]

export default function Aftercare() {
  usePageTitle("Aftercare")
  return (
    <main>
      <PageIntro title="Aftercare">
        <p>A simple healing routine protects the line, colour and skin beneath your tattoo.</p>
      </PageIntro>
      <section className="wrap page-body" aria-label="Healing stages">
        <div className="aftercare-timeline">
          {STAGES.map((stage) => (
            <article key={stage.range}>
                <div className="timeline-marker" aria-hidden="true"></div>
                <div>
                  <p className="timeline-range">{stage.range}</p>
                  <p>{stage.text}</p>
                </div>
            </article>
          ))}
        </div>
        <div className="slip aftercare-contact on-paper">
          <div>
            <h2>Something doesn't look right?</h2>
            <p>Write to the studio or call us.</p>
          </div>
          <Link className="button button-primary" to="/#contact">
            Write to the studio
          </Link>
        </div>
      </section>
    </main>
  )
}
