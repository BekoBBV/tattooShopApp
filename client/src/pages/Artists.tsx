import { Link } from "react-router"
import { PageIntro } from "../components/Layout"
import { HangingScroll } from "../components/Studio"
import { euros, firstSentence } from "../lib/format"
import type { Artist } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"

/**
 * The artists as hanging scrolls, each linking to their page, with the first line of their bio, their
 * rate, and two ways on: their flash to book, and their page with all their work.
 */
export default function Artists() {
  usePageTitle("Artists", "Meet the three artists at Blade & Brush in Sofia: bold traditional colour, black and grey, and fine line Japanese tattooing.")
  const artists = useLoad<Artist[]>("/artists")
  return (
    <main>
      <PageIntro title="Our artists">
        <p>Three artists, one shared foundation, and three very different hands.</p>
      </PageIntro>
      <section className="wrap page-body" aria-label="Artists">
        {artists.error && <p className="state-note">{artists.error}</p>}
        <div className="artists-page-grid">
          {(artists.data ?? []).map((artist) => (
            <article key={artist.id} className="artist-listing">
              <Link className="artist-scroll-link" to={`/artists/${artist.slug}`}>
                <HangingScroll name={artist.name} signature={artist.signaturePiece} />
                <h2>{artist.name}</h2>
                <p>{artist.style}</p>
              </Link>
              <p className="artist-listing-bio">{firstSentence(artist.bio)}</p>
              {artist.hourlyRate !== null && <p className="artist-listing-rate">Custom work from {euros(artist.hourlyRate)} per hour</p>}
              <div className="artist-listing-actions">
                <Link className="button button-primary" to={`/flash?artist=${artist.slug}`}>
                  Book with {artist.name}
                </Link>
                <Link className="button button-secondary" to={`/artists/${artist.slug}`}>
                  See {artist.name}'s work
                </Link>
              </div>
            </article>
          ))}
        </div>
      </section>
    </main>
  )
}
