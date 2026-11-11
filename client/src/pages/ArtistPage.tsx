import { useState } from "react"
import { Link, useParams } from "react-router"
import { BrushStroke, PageIntro } from "../components/Layout"
import { Lightbox } from "../components/Lightbox"
import { BookFlashButton, FlashCard, HangingScroll, ReviewCard, Stars } from "../components/Studio"
import { euros } from "../lib/format"
import type { Artist, Flash, ReviewSummary, Work } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"
import NotFound from "./NotFound"

/** One artist: their scroll, about and rates, their flash, their work and their reviews. */
export default function ArtistPage() {
  const { slug = "" } = useParams()
  const artist = useLoad<Artist>(`/artists/${encodeURIComponent(slug)}`)
  const sheet = useLoad<Flash[]>(artist.data ? `/flash?artistId=${artist.data.id}` : null)
  const works = useLoad<Work[]>(artist.data ? `/artists/${artist.data.slug}/works` : null)
  const reviews = useLoad<ReviewSummary>(artist.data ? `/reviews?artistId=${artist.data.id}` : null)
  const [viewing, setViewing] = useState<number | null>(null)
  usePageTitle(artist.data?.name ?? "Artist")

  if (artist.status === 404 && !artist.data) {
    return <NotFound />
  }
  const a = artist.data
  if (!a) {
    return (
      <main>
        <PageIntro title={artist.error ? "This page didn't load" : " "} view="sky">
          {artist.error && (
            <p>
              {artist.error}{" "}
              <button className="text-link" type="button" onClick={artist.reload}>
                Try again
              </button>
            </p>
          )}
        </PageIntro>
      </main>
    )
  }

  const rates = [
    a.hourlyRate !== null && `${euros(a.hourlyRate)} per hour`,
    a.dayRate !== null && `or ${euros(a.dayRate)} for a full day on large pieces`,
  ].filter(Boolean)

  return (
    <main>
      <PageIntro title={a.name} view="sky">
        <p>{a.style}</p>
      </PageIntro>
      <section className="wrap page-body artist-hero" aria-label={`About ${a.name}`}>
        <HangingScroll name={a.name} signature={a.signaturePiece} variant="wide" />
        <div>
          <h2>About</h2>
          <p>{a.bio}</p>
          <h2>Rates</h2>
          <p>Flash at fixed prices.{rates.length > 0 && ` Custom work ${rates.join(", ")}.`}</p>
          <div className="artist-actions">
            <Link className="button button-primary" to={`/flash?artist=${a.slug}`}>
              Book a flash design by {a.name}
            </Link>
            <Link className="button button-secondary" to={`/book/time?type=consultation&artist=${a.id}`}>
              Plan a custom piece with {a.name}
            </Link>
          </div>
        </div>
      </section>
      <BrushStroke />
      <section className="wrap artist-section" aria-labelledby="artist-flash-title">
        <h2 id="artist-flash-title">Flash by {a.name}</h2>
        {sheet.data && sheet.data.length === 0 ? (
          <p className="state-note">
            Everything by {a.name} is booked right now. <Link to={`/book/time?type=consultation&artist=${a.id}`}>Plan a custom piece</Link>{" "}
            instead.
          </p>
        ) : (
          <div className="flash-sheet">
            <span className="washi-tape is-left" aria-hidden="true"></span>
            <span className="washi-tape is-right" aria-hidden="true"></span>
            <div className="artist-flash-row">
              {(sheet.data ?? []).map((flash) => (
                <FlashCard key={flash.id} flash={flash} action={<BookFlashButton flash={flash} />} />
              ))}
            </div>
          </div>
        )}
      </section>
      <BrushStroke flipped />
      <section className="wrap artist-section" aria-labelledby="artist-work-title">
        <div className="section-heading">
          <div>
            <h2 id="artist-work-title">Work</h2>
          </div>
          {works.data && works.data.length > 0 && <p>Select a piece to view it larger.</p>}
        </div>
        {works.data && works.data.length > 0 ? (
          <div className="portfolio-grid">
            {works.data.map((work, i) => (
              <button key={work.id} type="button" aria-label={`View ${work.caption || `${a.name} work ${i + 1}`}`} onClick={() => setViewing(i)}>
                <img src={work.image.thumbUrl} alt="" loading="lazy" />
              </button>
            ))}
          </div>
        ) : (
          works.data && (
            <p className="work-soon">
              Photos of {a.name}'s finished work come soon. Until then, the flash above shows the style.
            </p>
          )
        )}
      </section>
      <BrushStroke />
      <section className="wrap artist-section reviews-section" aria-labelledby="artist-reviews-title">
        <div className="rating-block">
          <strong>{reviews.data && reviews.data.count > 0 ? reviews.data.averageRating.toFixed(1) : "New"}</strong>
          <span>{reviews.data && reviews.data.count > 0 ? "out of 5" : "no reviews yet"}</span>
          {reviews.data && reviews.data.count > 0 && <Stars rating={reviews.data.averageRating} size={22} />}
          <p>Example reviews: Blade &amp; Brush is a concept studio.</p>
        </div>
        <div>
          <h2 id="artist-reviews-title">Reviews</h2>
          <div className="review-list">
            {(reviews.data?.reviews ?? []).map((review) => (
              <ReviewCard key={review.id} review={review} />
            ))}
            {reviews.data && reviews.data.count === 0 && <p className="state-note">No reviews for {a.name} yet.</p>}
          </div>
        </div>
      </section>
      {viewing !== null && works.data && (
        <Lightbox
          photos={works.data.map((w, i) => ({ image: w.image, caption: w.caption || `${a.name} work ${i + 1}` }))}
          index={viewing}
          label={`${a.name}'s work`}
          onChange={setViewing}
          onClose={() => setViewing(null)}
        />
      )}
    </main>
  )
}
