import { useEffect } from "react"

const SITE = "Tattoo Studio"
const HOME_TITLE = "Tattoo Studio — Plovdiv Tattoo Studio"

/** Sets the browser tab title, e.g. "Pick a time · Tattoo Studio"; no title means the home title. */
export function usePageTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} · ${SITE}` : HOME_TITLE
  }, [title])
}
