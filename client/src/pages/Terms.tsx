import { Link } from "react-router"
import { PageIntro } from "../components/Layout"
import { studio } from "../lib/format"
import { usePageTitle } from "../lib/usePageTitle"

/**
 * How booking, deposits and cancelling work. The numbers are the server's booking rules
 * (app.booking.*): 18+, two upcoming bookings, 30 days ahead with 2 hours' notice, a €30 flash
 * deposit paid within 15 minutes, refunded when cancelled at least 48 hours ahead, session deposits
 * due within 3 days.
 */
export default function Terms() {
  usePageTitle("Booking terms", "How booking, deposits, cancelling and refunds work at Blade & Brush in Sofia.")
  return (
    <main>
      <PageIntro title="Booking terms">
        <p>How booking, deposits and cancelling work.</p>
      </PageIntro>
      <article className="wrap page-body legal-page">
        <h2>Who can book</h2>
        <ul>
          <li>You need to be 18 or older. Bring a photo ID to your appointment.</li>
          <li>You can have up to two upcoming bookings at a time, for each phone number or account.</li>
          <li>Book up to 30 days ahead, and at least 2 hours before the time you choose.</li>
        </ul>

        <h2>Flash designs</h2>
        <ul>
          <li>The price on the flash sheet is the price of the tattoo. Each design is tattooed only once.</li>
          <li>
            When you book, you pay a €30 deposit online. It is taken off the price, and the rest you pay in the studio {studio.payment}. Pay within
            15 minutes of booking, or the design and the time go back on the sheet.
          </li>
        </ul>

        <h2>Consultations and custom pieces</h2>
        <ul>
          <li>A consultation is free and takes 30 minutes. Your artist plans the piece and its sessions with you.</li>
          <li>
            For the sessions of a custom piece, your artist tells you the price and the deposit. The deposit is due within 3 days of
            reserving the session.
          </li>
        </ul>

        <h2>Cancelling</h2>
        <ul>
          <li>
            Cancel under <Link to="/manage">Manage a booking</Link> with your booking code and phone number, or under{" "}
            <Link to="/account">My account</Link>.
          </li>
          <li>Cancel at least 48 hours before your appointment and the deposit is refunded in full.</li>
          <li>Cancel later, or don't come, and the deposit is kept.</li>
          <li>If the studio has to cancel, your deposit is always refunded in full.</li>
        </ul>

        <h2>Your health and the consent form</h2>
        <p>
          When you book, you agree to the consent form shown in the booking steps. Tell your artist before you start about anything
          that affects your skin or healing, such as allergies, medication or pregnancy.
        </p>

        <h2>Designs</h2>
        <p>
          Flash designs and custom drawings are the artist's work. Bring your own idea and your artist will draw it in their style;
          the studio doesn't copy another artist's tattoo.
        </p>

        <p className="legal-note">
          Questions? <Link to="/#contact">Write to the studio</Link> or call <a href={studio.tel}>{studio.phone}</a>. How your
          details are kept is in the <Link to="/privacy">privacy policy</Link>. {studio.name} is a concept studio: this site shows
          how a real studio's terms would read.
        </p>
      </article>
    </main>
  )
}
