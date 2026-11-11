import { Link } from "react-router"
import { PageIntro } from "../components/Layout"
import { HangingScroll } from "../components/Studio"
import type { Artist } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"

/** The artists as hanging scrolls, each linking to their page. */
export default function Artists() {
  usePageTitle("Artists")
  const artists = useLoad<Artist[]>("/artists")
  return (
    <main>
      <PageIntro title="Our artists" view="sky">
        <p>Three artists, one shared foundation, and three very different hands.</p>
      </PageIntro>
      <section className="wrap page-body" aria-label="Artists">
        {artists.error && <p className="state-note">{artists.error}</p>}
        <div className="artists-page-grid">
          {(artists.data ?? []).map((artist) => (
            <Link key={artist.id} className="artist-scroll-link" to={`/artists/${artist.slug}`}>
              <HangingScroll name={artist.name} signature={artist.signaturePiece} />
              <h2>{artist.name}</h2>
              <p>{artist.style}</p>
            </Link>
          ))}
        </div>
      </section>
    </main>
  )
}
