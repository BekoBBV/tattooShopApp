import { Clock3, Paperclip, Phone, Send } from "lucide-react"
import { useId, useState, type FormEvent } from "react"
import { Link } from "react-router"
import { BrushStroke, KatanaDivider } from "../components/Layout"
import { KatanaCut } from "../components/KatanaCut"
import { ArtistSeal, FlashCard, FormError, HangingScroll, ReviewCard, Stars } from "../components/Studio"
import { api, ApiError } from "../lib/api"
import { studio } from "../lib/format"
import { limits } from "../lib/limits"
import type { Artist, Flash, ReviewSummary } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"
import { checkMessage, useFieldChecks } from "../lib/validation"

/** What the classic motifs mean, and which artist tattoos them. */
const MOTIFS = [
  { name: "Koi", meaning: "Perseverance. The koi that swims up the waterfall turns into a dragon.", artist: "Kai" },
  { name: "Dragon", meaning: "Strength and wisdom. In Japan the dragon is a water spirit that protects.", artist: "Kai" },
  { name: "Tiger", meaning: "Courage. Worn to keep away bad luck and illness.", artist: "Kai" },
  { name: "Hannya mask", meaning: "A woman turned demon by jealousy. Worn as protection against evil.", artist: "Dario" },
  { name: "Samurai and kabuto", meaning: "Discipline, honour and resolve. The helmet stands for the warrior who wore it.", artist: "Dario" },
  { name: "Peony", meaning: "Wealth and good fortune, and the nerve to live without fear.", artist: "Nika" },
  { name: "Cherry blossom", meaning: "Beauty that does not last. The flower of the samurai, who knew life could be short.", artist: "Nika" },
  { name: "Crane", meaning: "Long life and good fortune.", artist: "Nika" },
]

const FAQ = [
  { q: "Does it hurt?", a: "Yes, a little. Most people describe it as a hot scratch. Ribs, feet and the inner arm hurt more than the forearm, calf or thigh. You can take a break whenever you need one." },
  { q: "How clean is the studio?", a: "Needles, ink caps and gloves are single-use and opened in front of you. The station is disinfected and covered in fresh film before every client." },
  { q: "How old do I need to be?", a: "18 or older. Bring an ID to your appointment." },
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
  const reviews = useLoad<ReviewSummary>("/reviews")
  const [allMotifs, setAllMotifs] = useState(false)
  const motifsId = useId()
  const artistName = (id: string) => artists.data?.find((a) => a.id === id)?.name
  const slugOf = (name: string) => artists.data?.find((a) => a.name === name)?.slug ?? name.toLowerCase()

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
          <span className="washi-tape is-left" aria-hidden="true"></span>
          <span className="washi-tape is-right" aria-hidden="true"></span>
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
      <BrushStroke />

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
              {reviews.data.reviews.slice(0, 3).map((review) => (
                <div key={review.id} className="slip on-paper">
                  <ReviewCard review={review} artistName={artistName(review.artistId) ?? "the studio"} />
                </div>
              ))}
            </div>
          </section>
          <BrushStroke flipped />
        </>
      )}

      <section className="custom-cta" aria-labelledby="custom-title">
        <div className="noren">
          <div className="noren-rod" aria-hidden="true"></div>
          <div className="noren-cloth">
            <h2 id="custom-title">Want something of your own?</h2>
            <img className="noren-crest" src="/images/seal-bb.png" alt="" />
          </div>
        </div>
        <div className="custom-cta-body">
          <p>
            Bigger and custom pieces start with a free 30-minute consultation. Bring your idea and reference photos, and
            your artist will plan the piece and the sessions with you.
          </p>
          <Link className="button button-primary" to="/book/choose?type=consultation">
            Book a free consultation
          </Link>
        </div>
      </section>

      <section className="content-section motif-section" aria-labelledby="motif-title">
        <div className="section-heading">
          <div>
            <h2 id="motif-title">What the motifs mean</h2>
          </div>
          <p>
            Traditional Japanese tattoos are chosen for what they stand for, not only for how they look. Waves, wind and
            clouds fill the background and tie a piece together.
          </p>
        </div>
        <dl className={`motif-list${allMotifs ? " is-open" : ""}`} id={motifsId}>
          {MOTIFS.map((motif) => (
            <div key={motif.name}>
              <dt>{motif.name}</dt>
              <dd>
                <p>{motif.meaning}</p>
                <Link to={`/artists/${slugOf(motif.artist)}`} className="motif-artist">
                  <span className="seal-lockup">
                    <ArtistSeal name={motif.artist} />
                  </span>
                  Tattooed by {motif.artist}
                </Link>
              </dd>
            </div>
          ))}
        </dl>
        {!allMotifs && (
          <button className="button button-secondary motif-more" type="button" aria-controls={motifsId} aria-expanded="false"
            onClick={() => {
              setAllMotifs(true)
              // The button goes away: keyboard focus moves on to the first motif it revealed
              requestAnimationFrame(() => document.getElementById(motifsId)?.querySelectorAll<HTMLElement>("a")[4]?.focus())
            }}>
            Show all {MOTIFS.length} motifs
          </button>
        )}
      </section>

      <section className="content-section faq-section" aria-labelledby="faq-title">
        <div className="section-heading">
          <div>
            <h2 id="faq-title">Before your first tattoo</h2>
          </div>
          <p>The questions people ask most. Anything else, write to the studio.</p>
        </div>
        <div className="faq-list">
          {FAQ.map((item) => (
            <details key={item.q}>
              <summary>{item.q}</summary>
              <p>{item.a}</p>
            </details>
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
