import { RouterProvider } from "react-router"
import { router } from "./routes"

/** Jumps keyboard users past the header to the page's main heading. */
function SkipLink() {
  return (
    <a
      className="skip-link"
      href="#content"
      onClick={(event) => {
        event.preventDefault()
        const target = document.querySelector<HTMLElement>("main h1") ?? document.querySelector("main")
        if (!target) return
        target.setAttribute("tabindex", "-1")
        target.focus()
      }}
    >
      Skip to content
    </a>
  )
}

export default function App() {
  return (
    <>
      <SkipLink />
      <RouterProvider router={router} />
    </>
  )
}
