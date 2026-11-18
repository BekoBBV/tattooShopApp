import { Fragment, useSyncExternalStore } from "react"
import { Link, useSearchParams } from "react-router"
import { PageIntro } from "../components/Layout"
import { ArtistSeal, BookFlashButton, FlashCard } from "../components/Studio"
import type { Artist, Flash, FlashSize } from "../lib/types"
import { useLoad } from "../lib/useLoad"
import { usePageTitle } from "../lib/usePageTitle"

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

const SIZES: { value: FlashSize; label: string }[] = [
  { value: "SMALL", label: "Small" },
  { value: "MEDIUM", label: "Medium" },
  { value: "LARGE", label: "Large" },
]

// The gallery's columns, as in the CSS: 4 on desktops, 3 on tablets, 1 on phones
const DESKTOP = "(min-width: 1024px)"
const TABLET = "(min-width: 768px)"
const columnsNow = () => (window.matchMedia(DESKTOP).matches ? 4 : window.matchMedia(TABLET).matches ? 3 : 1)
function onColumnsChange(callback: () => void) {
  const queries = [DESKTOP, TABLET].map((q) => window.matchMedia(q))
  queries.forEach((q) => q.addEventListener("change", callback))
  return () => queries.forEach((q) => q.removeEventListener("change", callback))
}
const useGalleryColumns = () => useSyncExternalStore(onColumnsChange, columnsNow, () => 4)

/** The flash sheet: every design that can be booked now, filtered by artist and size. */
export default function FlashGallery() {
  usePageTitle("Flash designs", "Japanese flash designs you can book at a fixed price at Blade & Brush in Sofia: koi, dragons, masks, peonies and more, each tattooed only once.")
  const [params, setParams] = useSearchParams()
  const artists = useLoad<Artist[]>("/artists")
  const sheet = useLoad<Flash[]>("/flash")
  const artistSlug = params.get("artist")
  const sizeFilter = params.get("size") as FlashSize | null
  const artist = artists.data?.find((a) => a.slug === artistSlug) ?? null
  const slugOf = (name: string) => artists.data?.find((a) => a.name === name)?.slug ?? name.toLowerCase()

  const shown = (sheet.data ?? []).filter(
    (f) => (!artistSlug || f.artistId === artist?.id) && (!sizeFilter || f.size === sizeFilter),
  )
  // The consultation band goes between whole rows, about halfway down, so no row is left half empty
  const columns = useGalleryColumns()
  const bandAt = columns * Math.round(Math.ceil(shown.length / columns) / 2)

  const setFilter = (key: "artist" | "size", value: string | null) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  const customBand = (
    <aside className="custom-band">
      <p>Want something of your own? Book a free consultation to plan your tattoo.</p>
      <Link className="button button-secondary" to="/book/choose?type=consultation">
        Book a free consultation
      </Link>
    </aside>
  )

  return (
    <main>
      <PageIntro title="Flash designs" painting="seal">
        <p>One-of-a-kind designs at a fixed price. Each is tattooed once: when it is booked, it comes off the sheet.</p>
      </PageIntro>
      <section className="wrap page-body" aria-label="Flash designs">
        <div className="filter-row">
          <fieldset>
            <legend>Artist</legend>
            <div className="toggles">
              <Toggle active={!artistSlug} onClick={() => setFilter("artist", null)}>
                All
              </Toggle>
              {(artists.data ?? []).map((a) => (
                <Toggle key={a.id} active={artistSlug === a.slug} onClick={() => setFilter("artist", a.slug)}>
                  {a.name}
                </Toggle>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend>Size</legend>
            <div className="toggles">
              <Toggle active={!sizeFilter} onClick={() => setFilter("size", null)}>
                All
              </Toggle>
              {SIZES.map((s) => (
                <Toggle key={s.value} active={sizeFilter === s.value} onClick={() => setFilter("size", s.value)}>
                  {s.label}
                </Toggle>
              ))}
            </div>
          </fieldset>
        </div>
        {sheet.error && <p className="state-note">{sheet.error}</p>}
        {sheet.loading && !sheet.data && <p className="state-note">Loading the flash sheet...</p>}
        {sheet.data && shown.length === 0 ? (
          <div className="empty-filter">
            <p className="state-note">
              No flash designs match these filters. Clear the filters or book a consultation for a custom piece.
            </p>
            <div className="artist-actions">
              <button className="button button-secondary" type="button" onClick={() => setParams({}, { replace: true })}>
                Clear the filters
              </button>
              <Link className="button button-primary" to="/book/choose?type=consultation">
                Book a free consultation
              </Link>
            </div>
          </div>
        ) : (
          sheet.data && (
            <div className="flash-sheet">
              <span className="sheet-pin is-left" aria-hidden="true"></span>
              <span className="sheet-pin is-right" aria-hidden="true"></span>
              <h2 className="visually-hidden">{artist ? `Flash by ${artist.name}` : "All flash designs"}</h2>
              <div className="gallery-grid">
                {shown.map((flash, i) => (
                  <Fragment key={flash.id}>
                    {i === bandAt && shown.length > 4 && customBand}
                    <FlashCard flash={flash} action={<BookFlashButton flash={flash} />} />
                  </Fragment>
                ))}
              </div>
            </div>
          )
        )}
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
        <dl className="motif-list is-open">
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
      </section>
    </main>
  )
}

function Toggle({ active, onClick, children }: { active: boolean; onClick: () => void; children: string }) {
  return (
    <button type="button" className={`filter-toggle${active ? " is-active" : ""}`} aria-pressed={active} onClick={onClick}>
      {children}
    </button>
  )
}
