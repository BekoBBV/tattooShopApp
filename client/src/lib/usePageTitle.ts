import { useEffect } from "react"

const SITE = "Blade & Brush"
const HOME_TITLE = "Blade & Brush - Traditional Japanese Tattooing in Sofia"

/** Sets the browser tab title, e.g. "Flash designs - Blade & Brush"; no title means the home title. */
export function usePageTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} - ${SITE}` : HOME_TITLE
  }, [title])
}
