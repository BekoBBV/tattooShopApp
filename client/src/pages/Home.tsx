import { AtSign, Clock3, MapPin, Paperclip, Phone, Send } from "lucide-react"
import { useState, type FormEvent } from "react"
import { Link } from "react-router"
import { KatanaDivider, SectionRule } from "../components/Layout"
import { KatanaCut } from "../components/KatanaCut"
import { Art, ArtistSeal, FlashCard, FormError, ReviewCard, Stars } from "../components/Studio"
import { PIECE_FOCUS } from "../lib/pieces"
import { api, ApiError } from "../lib/api"
import { firstSentence, instagramUrl, studio } from "../lib/format"
import { limits } from "../lib/limits"
import type { Artist, Flash, RecentWork, ReviewSummary } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"
import { checkMessage, useFieldChecks } from "../lib/validation"

/** Designs for the home page sheet (four, one row), taking turns between the artists. */
function pickDesigns(sheet: Flash[], count = 4) {
  const byArtist = new Map<string, Flash[]>()
  sheet.forEach((f) => byArtist.set(f.artistId, [...(byArtist.get(f.artistId) ?? []), f]))
  const queues = [...byArtist.values()]
  const picked: Flash[] = []
  for (let round = 0; picked.length < count && queues.some((q) => q.length > round); round++) {
    queues.forEach((q) => q[round] && picked.length < count && picked.push(q[round]))
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
                    <source srcSet="/images/hero-night.webp" type="image/webp" />
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
          <div className="flash-grid is-one-row">
            {pickDesigns(sheet.data ?? []).map((flash) => (
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
          <p className="example-note">Example work: Blade &amp; Brush is a concept studio.</p>
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
        {/* Compact cards: the signature piece, the name and style, and the first line of the bio */}
        <div className="artist-cards">
          {(artists.data ?? []).map((artist) => (
            <Link key={artist.id} to={`/artists/${artist.slug}`} className="artist-compact">
              <div className="artist-compact-art">
                <Art image={artist.signaturePiece} fit="cover" focus={artist.signaturePiece ? PIECE_FOCUS[artist.signaturePiece.id] : undefined} />
              </div>
              <div className="artist-compact-text">
                <h3>
                  <ArtistSeal name={artist.name} /> {artist.name}
                </h3>
                <p className="artist-compact-style">{artist.style}</p>
                <p>{firstSentence(artist.bio)}</p>
              </div>
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

      {/* A slim banner: custom pieces start with a consultation */}
      <section className="custom-banner" aria-labelledby="custom-title">
        <div className="custom-banner-text">
          <h2 id="custom-title">Want something of your own?</h2>
          <p>Custom pieces start with a free 30-minute consultation. Bring your idea and reference photos.</p>
        </div>
        <Link className="button button-primary" to="/book/choose?type=consultation">
          Book a free consultation
        </Link>
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
        <p className="contact-faq-link">
          First tattoo? Read <Link to="/aftercare#before-your-first-tattoo">the questions people ask most</Link>.
        </p>
        <div className="contact-detail">
          <Clock3 className="icon" size={20} aria-hidden="true" />
          <span>
            Tuesday to Sunday
            <br />
            11:00 to 20:00
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
