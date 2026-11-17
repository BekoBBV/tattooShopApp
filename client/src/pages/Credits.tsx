import { PageIntro } from "../components/Layout"
import { usePageTitle } from "../lib/usePageTitle"

/** Where the site's pictures come from. Rows are filled in as the final images are confirmed. */
const CREDITS = [
  { file: "Night torii painting (home page)", author: "To be confirmed", source: "To be confirmed", licence: "To be confirmed" },
  { file: "Sample flash drawings (all twelve designs on the flash sheet)", author: "To be confirmed", source: "To be confirmed", licence: "To be confirmed" },
  { file: "Tattoo machine drawing (Flash page, phone menu) and scroll pictures: stone lantern, pagoda (Aftercare and Not found)", author: "Blade & Brush", source: "Drawn for the site", licence: "All rights reserved" },
  { file: "BB seal (logo)", author: "Blade & Brush", source: "Studio artwork", licence: "All rights reserved" },
  { file: "Flash designs and portfolio photos the artists upload", author: "The studio's artists", source: "Their own work", licence: "All rights reserved" },
]

export default function Credits() {
  usePageTitle("Image credits", "Where the pictures on the Blade & Brush website come from, and their licences.")
  return (
    <main>
      <PageIntro title="Image credits">
        <p>Sources and licences for visual material used by the studio.</p>
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
    </main>
  )
}
