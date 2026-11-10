import React from "react"
import ReactDOM from "react-dom/client"
// The design's fonts are bundled with the site (Latin letters only, no Google Fonts request)
import "@fontsource/zen-old-mincho/latin-600.css"
import "@fontsource/zen-old-mincho/latin-900.css"
import "@fontsource/zen-kaku-gothic-new/latin-400.css"
import "@fontsource/zen-kaku-gothic-new/latin-500.css"
import "@fontsource/zen-kaku-gothic-new/latin-700.css"
import App from "./app/App"
import "./index.css"

async function start() {
  // `npm run dev:mock` answers the API from sample data in the browser, so the site can be tried
  // without the server and its database (never part of a normal build)
  if (import.meta.env.VITE_MOCK_API === "true") {
    const { installMockApi } = await import("./mock/mockApi")
    installMockApi()
  }
  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  )
}

void start()
