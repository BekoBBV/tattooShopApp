import { AtSign, Clock3, MapPin, Paperclip, Phone, Send } from "lucide-react"
import { useState, type FormEvent } from "react"
import { Link } from "react-router"
import { KatanaDivider, SectionRule } from "../components/Layout"
import { KatanaCut } from "../components/KatanaCut"
import { ScrollArt } from "../components/ScrollArt"
import { ArtistSeal, FlashCard, FormError, HangingScroll, ReviewCard, Stars, WallScroll } from "../components/Studio"
import { api, ApiError } from "../lib/api"
import { instagramUrl, studio } from "../lib/format"
import { limits } from "../lib/limits"
import type { Artist, Flash, RecentWork, ReviewSummary } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"
import { checkMessage, useFieldChecks } from "../lib/validation"

const FAQ = [
  { q: "Does it hurt?", a: "Yes, a little. Most people describe it as a hot scratch. Ribs, feet and the inner arm hurt more than the forearm, calf or thigh. You can take a break whenever you need one." },
  { q: "How clean is the studio?", a: "Needles, ink caps and gloves are single-use and opened in front of you. The station is disinfected and covered in fresh film before every client." },
  { q: "What should I bring?", a: "A photo ID that shows you are 18 or older. Eat beforehand, and wear clothes that leave the spot free. For a custom piece, bring your reference pictures." },
  { q: "Do you take walk-ins?", a: "Sometimes. If an artist has a free hour, they can tattoo a small flash design the same day, so call the studio first. To be sure of a time, book online." },
  { q: "How do I pay?", a: `For a flash design you pay a €30 deposit online when you book, with Revolut, a card, Apple Pay or Google Pay. It is taken off the price, and the rest you pay in the studio ${studio.payment}. For a custom piece, your artist tells you the price of each session and its deposit.` },
  { q: "Can I bring my own design?", a: "Yes. Book a free consultation and your artist will redraw your idea in their own style. We don't copy another artist's tattoo." },
  { q: "How long does healing take?", a: "The surface heals in two to four weeks and the skin settles within a month or two. The aftercare guide walks you through each day." },
  { q: "Can you cover an old tattoo?", a: "Often, yes. Bring photos to a free consultation and the artist will tell you what is possible." },
]

/** Six designs for the home page sheet, taking turns between the artists. */
function pickSix(sheet: Flash[]) {
  const byArtist = new Map<string, Flash[]>()
  sheet.forEach((f) => byArtist.set(f.artistId, [...(byArtist.get(f.artistId) ?? []), f]))
  const queues = [...byArtist.values()]
  const picked: Flash[] = []
  for (let round = 0; picked.length < 6 && queues.some((q) => q.length > round); round++) {
    queues.forEach((q) => q[round] && picked.length < 6 && picked.push(q[round]))
  }
  return picked
}

export default function Home() {
  usePageTitle()
  const sheet = useLoad<Flash[]>("/flash")
  const artists = useLoad<Artist[]>("/artists")
  const reviews = useLoad<ReviewSummary>("/reviews?limit=3")
  const work = useLoad<RecentWork[]>("/works?limit=8")
  const artistName = (id: string) => artists.data?.find((a) => a.id === id)?.name

  return (
    <main>
      <section className="hero" aria-labelledby="hero-title">
        <KatanaCut>
              <div className="hero-image">
                <div className="scene-base">
                  <picture className="hero-picture">

                    <img src="/images/footer-night.jpg" alt="A red torii gate in a misty night mountain landscape" />
                  </picture>
                </div>
              </div>
              <div className="hero-copy">
                <h1 id="hero-title">Japanese tattooing in Sofia</h1>
                <p>Flash designs you can book today. Custom pieces planned with your artist.</p>
                <div className="hero-buttons">
                  <Link className="button button-primary" to="/flash" data-katana>
                    Book a flash design
                  </Link>
                  <Link
                    className="button button-secondary"
                    to="/book/choose?type=consultation"
                    data-katana
                  >
                    Plan a custom piece
                  </Link>
                </div>
              </div>
        </KatanaCut>
      </section>

      <section className="content-section flash-section" aria-labelledby="flash-title">
        <div className="section-heading">
          <div>
            <h2 id="flash-title">Flash you can book today</h2>
          </div>
          <p>Every design is one of a kind. It is tattooed once, and when it is booked it comes off the sheet.</p>
        </div>
        <div className="flash-sheet">
          <span className="sheet-pin is-left" aria-hidden="true"></span>
          <span className="sheet-pin is-right" aria-hidden="true"></span>
          {sheet.error && <p className="state-note">{sheet.error}</p>}
          {sheet.data && sheet.data.length === 0 && <p className="state-note">Every design is booked right now. New flash comes soon.</p>}
          <div className="flash-grid">
            {pickSix(sheet.data ?? []).map((flash) => (
              <FlashCard key={flash.id} flash={flash} byline={false} aboutTime={false} href={`/book/time?flash=${flash.id}`} />
            ))}
          </div>
        </div>
        <div className="flash-how">
          <p>Pick a design, choose a time and pay a €30 deposit with Revolut. The rest you pay in the studio.</p>
          <Link className="text-link" to="/flash">
            See all flash designs
          </Link>
        </div>
      </section>

      {/* The newest photos of finished tattoos; hidden until the artists have uploaded some */}
      {work.data && work.data.length > 0 && (
        <section className="content-section recent-work-section" aria-labelledby="recent-work-title">
          <div className="section-heading">
            <div>
              <h2 id="recent-work-title">Recent work</h2>
            </div>
            <p>Finished tattoos from the studio. Select one to see more by that artist.</p>
          </div>
          <ul className="recent-work-grid">
            {work.data.map((w) => (
              <li key={w.id}>
                <Link to={`/artists/${w.artistSlug}`}>
                  <img src={w.image.thumbUrl} alt={w.caption} loading="lazy" />
                  <span className="recent-work-caption">
                    {w.caption}
                    <span>by {w.artistName}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
      <KatanaDivider />


      <section className="content-section artists-section" aria-labelledby="artists-title">
        <div className="section-heading">
          <div>
            <h2 id="artists-title">Three artists, three styles</h2>
          </div>
          <p>Kai works in bold colour, Dario in black and grey, Nika in fine line.</p>
        </div>
        <div className="artist-grid">
          {(artists.data ?? []).map((artist) => (
            <Link key={artist.id} to={`/artists/${artist.slug}`} className="artist-card">
              <HangingScroll name={artist.name} signature={artist.signaturePiece} variant="mini" />
              <div>
                <span className="seal-lockup">
                  <ArtistSeal name={artist.name} />
                </span>
                <h3>{artist.name}</h3>
              </div>
              <p>{artist.style}</p>
            </Link>
          ))}
        </div>
      </section>
      <SectionRule />

      {reviews.data && reviews.data.count > 0 && (
        <>
          <section className="content-section home-reviews" aria-labelledby="reviews-title">
            <div className="section-heading">
              <div>
                <h2 id="reviews-title">What clients say</h2>
              </div>
              <p>
                <Stars rating={reviews.data.averageRating} size={20} />
                {reviews.data.averageRating.toFixed(1)} out of 5 from {reviews.data.count} {reviews.data.count === 1 ? "review" : "reviews"}.
                Example reviews: Blade &amp; Brush is a concept studio.
              </p>
            </div>
            <div className="home-review-grid">
              {reviews.data.reviews.map((review) => (
                <div key={review.id} className="slip on-paper">
                  <ReviewCard review={review} artistName={artistName(review.artistId)} />
                </div>
              ))}
            </div>
          </section>
          <SectionRule />
        </>
      )}

      <section className="custom-cta has-scroll" aria-labelledby="custom-title">
        <WallScroll className="cta-scroll" hidden>
          <ScrollArt kind="seal" />
        </WallScroll>
        <div className="custom-cta-body">
          <h2 id="custom-title">Want something of your own?</h2>
          <p>
            Bigger and custom pieces start with a free 30-minute consultation. Bring your idea and reference photos, and
            your artist will plan the piece and the sessions with you.
          </p>
          <Link className="button button-primary" to="/book/choose?type=consultation">
            Book a free consultation
          </Link>
        </div>
      </section>

      <section className="content-section faq-section" aria-labelledby="faq-title">
        <div className="section-heading">
          <div>
            <h2 id="faq-title">Before your first tattoo</h2>
          </div>
          <p>The questions people ask most. Anything else, write to the studio.</p>
        </div>
        {/* Two fixed halves, so opening an answer never moves a question to the other column */}
        <div className="faq-list is-split">
          {[FAQ.slice(0, Math.ceil(FAQ.length / 2)), FAQ.slice(Math.ceil(FAQ.length / 2))].map((half, i) => (
            <div className="faq-column" key={i}>
              {half.map((item) => (
                <details key={item.q}>
                  <summary>{item.q}</summary>
                  <p>{item.a}</p>
                </details>
              ))}
            </div>
          ))}
        </div>
      </section>

      <ContactSection />
    </main>
  )
}

/** "Write to the studio": name, email or phone, the message and up to 5 photos. */
function ContactSection() {
  const [values, setValues] = useState({ name: "", contact: "", text: "" })
  const [photos, setPhotos] = useState<File[]>([])
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)
  const [problem, setProblem] = useState("")
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const errors = checkMessage(values, photos.length)
  const checks = useFieldChecks(errors, serverErrors)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!checks.canSubmit(event.currentTarget)) return
    setSending(true)
    setProblem("")
    setServerErrors({})
    try {
      const message = { name: values.name.trim(), contact: values.contact.trim(), text: values.text.trim() }
      if (photos.length) {
        const form = new FormData()
        form.append("message", new Blob([JSON.stringify(message)], { type: "application/json" }))
        photos.forEach((photo) => form.append("photos", photo))
        await api("/messages", { method: "POST", body: form })
      } else {
        await api("/messages", { method: "POST", body: JSON.stringify(message) })
      }
      setSent(true)
      setValues({ name: "", contact: "", text: "" })
      setPhotos([])
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setServerErrors(reason.errors)
      setProblem(reason instanceof Error ? reason.message : "Something went wrong. Please try again.")
    } finally {
      setSending(false)
    }
  }

  return (
    <section className="contact-wrap" id="contact" aria-labelledby="contact-title">
      <div className="contact-intro">
        <h2 id="contact-title">Write to the studio</h2>
        <p>
          Questions about placement, size or an existing tattoo? Tell us what you have in mind. We usually reply within two
          working days.
        </p>
        <div className="contact-detail">
          <Clock3 className="icon" size={20} aria-hidden="true" />
          <span>
            Tuesday to Sunday
            <br />
            11:00 to 23:00
          </span>
        </div>
        <div className="contact-detail">
          <Phone className="icon" size={20} aria-hidden="true" />
          <a href={studio.tel}>{studio.phone}</a>
        </div>
        <div className="contact-detail">
          <MapPin className="icon" size={20} aria-hidden="true" />
          <span>
            {studio.address}
            <br />
            <a href={studio.map} target="_blank" rel="noopener noreferrer">
              Open in Maps
            </a>
          </span>
        </div>
        {studio.instagram && (
          <div className="contact-detail">
            <AtSign className="icon" size={20} aria-hidden="true" />
            <a href={instagramUrl(studio.instagram)} target="_blank" rel="noopener noreferrer">
              {studio.instagram} on Instagram
            </a>
          </div>
        )}
      </div>
      <form className="contact-form" onSubmit={submit} onChange={checks.onChange} onBlur={checks.onBlur} noValidate>
        {sent && (
          <p className="form-sent" role="status">
            Thank you, your message is on its way. We usually reply within two working days.
          </p>
        )}
        <FormError>{problem}</FormError>
        <label>
          Name
          <input
            name="name"
            autoComplete="name"
            maxLength={limits["message.name"]}
            value={values.name}
            onChange={(e) => setValues({ ...values, name: e.target.value })}
            aria-invalid={checks.errorFor("name") ? true : undefined}
          />
          {checks.errorFor("name") && <span className="field-error">{checks.errorFor("name")}</span>}
        </label>
        <label>
          Email or phone
          <input
            name="contact"
            autoComplete="email"
            maxLength={limits["message.contact"]}
            value={values.contact}
            onChange={(e) => setValues({ ...values, contact: e.target.value })}
            aria-invalid={checks.errorFor("contact") ? true : undefined}
          />
          {checks.errorFor("contact") && <span className="field-error">{checks.errorFor("contact")}</span>}
        </label>
        <label>
          Message
          <textarea
            name="text"
            rows={5}
            maxLength={limits["message.text"]}
            value={values.text}
            onChange={(e) => setValues({ ...values, text: e.target.value })}
            aria-invalid={checks.errorFor("text") ? true : undefined}
          ></textarea>
          {checks.errorFor("text") && <span className="field-error">{checks.errorFor("text")}</span>}
        </label>
        <div>
          <label className="file-button">
            <Paperclip className="icon" size={20} aria-hidden="true" /> Attach photos
            <input
              type="file"
              name="photos"
              multiple
              accept="image/jpeg,image/png,image/heic,image/heif,.heic,.heif"
              onChange={(e) => setPhotos([...(e.target.files ?? [])])}
            />
          </label>
          <p className="helper">
            {photos.length > 0 ? `${photos.length} ${photos.length === 1 ? "photo" : "photos"} attached. ` : ""}
            Up to 5 photos, 10 MB each.
          </p>
          {checks.errorFor("photos") && <p className="field-error">{checks.errorFor("photos")}</p>}
        </div>
        <button className="button button-primary submit-button" type="submit" disabled={sending}>
          {sending ? "Sending..." : "Send message"} <Send className="icon" size={18} aria-hidden="true" />
        </button>
      </form>
    </section>
  )
}
