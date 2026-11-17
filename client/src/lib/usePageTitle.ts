import { useEffect } from "react"

const SITE = "Blade & Brush"
const HOME_TITLE = "Blade & Brush - Traditional Japanese Tattooing in Sofia"
/** The home page's description (the same as in index.html), and the one for pages that have none of their own */
export const SITE_DESCRIPTION =
  "Blade & Brush, a traditional Japanese tattoo studio in Sofia: see the artists and their flash designs, book a flash tattoo or a free consultation online."

/** Sets a meta tag's content, adding the tag if the page has none yet. */
function setMeta(attribute: "name" | "property", key: string, content: string) {
  let tag = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
  if (!tag) {
    tag = document.createElement("meta")
    tag.setAttribute(attribute, key)
    document.head.appendChild(tag)
  }
  tag.content = content
}

/**
 * Sets the browser tab title, e.g. "Flash designs - Blade & Brush" (no title means the home title),
 * and the page's description, which search results and link previews show under the title (no
 * description means the site's own).
 */
export function usePageTitle(title?: string, description?: string) {
  useEffect(() => {
    const fullTitle = title ? `${title} - ${SITE}` : HOME_TITLE
    const text = description ?? SITE_DESCRIPTION
    document.title = fullTitle
    setMeta("name", "description", text)
    setMeta("property", "og:title", fullTitle)
    setMeta("property", "og:description", text)
  }, [title, description])
}
