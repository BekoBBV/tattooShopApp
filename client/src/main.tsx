import React from "react"
import ReactDOM from "react-dom/client"
// Fonts are bundled with the site (no Google Fonts request), so it also works offline
import "@fontsource/inter/400.css"
import "@fontsource/inter/500.css"
import "@fontsource/inter/600.css"
import "@fontsource/inter/700.css"
import "@fontsource/oswald/500.css"
import "@fontsource/oswald/600.css"
import "@fontsource/oswald/700.css"
import App from "./app/App"
import "./index.css"

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
