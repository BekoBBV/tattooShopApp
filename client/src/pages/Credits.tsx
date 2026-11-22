import { Link } from "react-router"
import { PageIntro } from "../components/Layout"
import { usePageTitle } from "../lib/usePageTitle"

/** Where the site's pictures come from. Rows are filled in as the final images are confirmed. */
const CREDITS = [
  { file: "Night torii painting (home page)", author: "To be confirmed", source: "To be confirmed", licence: "To be confirmed" },
  { file: "Sample flash drawings (all twelve designs on the flash sheet)", author: "To be confirmed", source: "To be confirmed", licence: "To be confirmed" },
  { file: "Artists' scroll pictures: dragon (Kai), demon mask (Dario), crane (Nika)", author: "Blade & Brush", source: "AI-generated for the site", licence: "Under the image tool's terms" },
  { file: "Photos of finished tattoos (Recent work and the artists' Work)", author: "Other tattoo artists, not credited", source: "Collected from Pinterest for this concept project", licence: "Not licensed: examples only" },
  { file: "Tattoo machine drawing (Flash page, phone menu) and scroll pictures: the studio scroll (Aftercare), pagoda (Not found)", author: "Blade & Brush", source: "Drawn for the site", licence: "All rights reserved" },
  { file: "BB seal (logo)", author: "Blade & Brush", source: "Studio artwork", licence: "All rights reserved" },
  { file: "Flash designs and portfolio photos the artists upload", author: "The studio's artists", source: "Their own work", licence: "All rights reserved" },
]

export default function Credits() {
  usePageTitle("Image credits", "Where the pictures on the Blade & Brush website come from, and their licences.")
  return (
    <main>
      <PageIntro title="Image credits">
        <p>Where the site's pictures, fonts and icons come from.</p>
      </PageIntro>
      <section className="wrap credits-shell">
        <table className="credits-table" aria-label="Image credits">
          <thead>
            <tr>
              <th>File</th>
              <th>Author</th>
              <th>Source</th>
              <th>Licence</th>
            </tr>
          </thead>
          <tbody>
            {CREDITS.map((row) => (
              <tr key={row.file}>
                <td data-label="File">{row.file}</td>
                <td data-label="Author">{row.author}</td>
                <td data-label="Source">{row.source}</td>
                <td data-label="Licence">{row.licence}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      <section className="wrap page-body legal-page credits-about" aria-labelledby="about-project-title">
        <h2 id="about-project-title">About this project</h2>
        <p>
          Blade &amp; Brush is a concept: a booking website for an imaginary Japanese tattoo studio in Sofia, built as a
          project. It is not a real business.
        </p>
        <ul>
          <li>
            <strong>Made up:</strong> the studio, its address and phone number, the Instagram handles, the three artists, the
            reviews and the prices. Bookings, deposits and messages on this site are not real, and no money is taken.
          </li>
          <li>
            <strong>Tattoo photos:</strong> the finished tattoos under Recent work and on the artists' pages are by other tattoo
            artists. They were collected from Pinterest as examples, and their makers are not known. If one of them is your
            work and you would like it credited or removed, <Link to="/#contact">write to us</Link>.
          </li>
          <li>
            <strong>AI-generated:</strong> the pictures on the artists' scrolls (the dragon, the demon mask and the crane).
          </li>
          <li>
            <strong>Drawn for the site:</strong> the BB seal, the studio scroll, the pagoda and the tattoo machine.
          </li>
          <li>
            <strong>Fonts and icons:</strong> Zen Old Mincho and Zen Kaku Gothic New (SIL Open Font License, through
            Fontsource) and Lucide icons (ISC licence).
          </li>
        </ul>
      </section>
    </main>
  )
}
