import { Link } from "react-router"
import { PageIntro } from "../components/Layout"
import { studio } from "../lib/format"
import { usePageTitle } from "../lib/usePageTitle"

/**
 * What the studio keeps about people, why and for how long. Every number here is what the server does:
 * booking details are wiped 180 days after the appointment (app.booking.anonymize-after), messages
 * after 90 days (app.messages.keep-for), and the site sets no cookies.
 */
export default function Privacy() {
  usePageTitle("Privacy policy", "What Blade & Brush keeps about you when you book, write or make an account, why, and for how long.")
  return (
    <main>
      <PageIntro title="Privacy policy">
        <p>What the studio keeps about you, why, and for how long.</p>
      </PageIntro>
      <article className="wrap page-body legal-page">
        <h2>Who keeps your details</h2>
        <p>
          {studio.name}, {studio.address}. To ask anything about your details, <Link to="/#contact">write to the studio</Link> or
          call <a href={studio.tel}>{studio.phone}</a>.
        </p>

        <h2>What we keep and why</h2>
        <ul>
          <li>
            <strong>When you book:</strong> your name, phone number, email and date of birth, to hold your appointment, check that
            you are 18 or older and reach you about it. For a consultation, also your idea, the placement, size and colour, and any
            reference photos, so your artist can prepare.
          </li>
          <li>
            <strong>If you make an account:</strong> your name, email, phone number and date of birth, and your password, which is
            stored scrambled so that nobody can read it, the studio included.
          </li>
          <li>
            <strong>When you write to the studio:</strong> your name, how to reach you, your message and any photos you attach.
          </li>
          <li>
            <strong>When you leave a review:</strong> the name you give, the stars and what you wrote. Reviews show on the site after
            the studio has read them.
          </li>
          <li>
            <strong>When you pay a deposit:</strong> you pay on the payment provider's own page. The site never sees your card
            details, only whether the deposit was paid.
          </li>
        </ul>

        <h2>How long we keep them</h2>
        <ul>
          <li>
            Your name, phone, email, date of birth, notes, idea and photos are erased from a booking 180 days after the appointment.
            What is left (the artist, the time, the price) says nothing about you.
          </li>
          <li>Messages to the studio are deleted after 90 days.</li>
          <li>
            An account stays until you delete it, under <Link to="/account">My account</Link>. Your bookings then leave the account;
            an upcoming one still works with its booking code and your phone number.
          </li>
        </ul>

        <h2>Who else handles them</h2>
        <p>
          Only the services that run the site: the hosting and database, the storage for pictures, the email service that sends your
          booking confirmation and a reminder the day before, and the payment provider. Your details are never sold, shared for
          advertising or used for newsletters.
        </p>

        <h2>Cookies</h2>
        <p>
          The site sets no cookies and uses no trackers. Your browser keeps two things on your device: your sign-in, until you sign
          out, and the booking you are in the middle of, until you close the tab.
        </p>

        <h2>Your rights</h2>
        <p>
          You can ask to see, correct or delete what the studio keeps about you. You can change or delete your account yourself
          under <Link to="/account">My account</Link>; for a booking or a message, <Link to="/#contact">write to the studio</Link>.
          If you think your details are mishandled, you can complain to Bulgaria's Commission for Personal Data Protection
          (<a href="https://www.cpdp.bg/" target="_blank" rel="noopener noreferrer">cpdp.bg</a>).
        </p>

        <p className="legal-note">
          {studio.name} is a concept studio: this site shows how a real studio's policy would read. See also the{" "}
          <Link to="/terms">booking terms</Link>.
        </p>
      </article>
    </main>
  )
}
