import { useEffect, useRef, useState, type CSSProperties, type MouseEvent, type ReactNode } from "react"
import { useNavigate } from "react-router"

const SEEN = "blade-and-brush-katana-cut"

const PETALS = Array.from({ length: 12 }, (_, i) => ({
  // Spread along the cut, drifting down and sideways
  along: 8 + i * 7.5 + ((i * 37) % 5),
  dx: ((i * 53) % 90) - 45,
  delay: (i % 4) * 0.04,
  turn: 120 + ((i * 71) % 160),
}))

/**
 * The katana cut on the home page: the first time a visitor presses one of the hero buttons in a
 * browser session, a sword stroke slices the painting from the top-right to the bottom-left corner,
 * the halves slide apart, a few petals fall, and then the next page opens. After that the buttons
 * go straight there. With reduced motion it is a short fade.
 *
 * Links inside it that should play the cut carry `data-katana`.
 */
export function KatanaCut({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const stage = useRef<HTMLDivElement>(null)
  const [cut, setCut] = useState<null | { angle: number; length: number; height: number; reduced: boolean }>(null)
  const timer = useRef<number | undefined>(undefined)

  // Leaving the page before the cut finishes must not pull the visitor to its destination later
  useEffect(() => () => window.clearTimeout(timer.current), [])

  // Capture phase: runs before the link itself, so preventDefault stops its navigation
  function onClickCapture(event: MouseEvent<HTMLDivElement>) {
    const link = (event.target as HTMLElement).closest<HTMLAnchorElement>("a[data-katana]")
    const to = link?.getAttribute("href")
    if (!to) return
    // A new tab or window opens straight away and doesn't use up the one cut per visit
    if (cut || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
    try {
      if (sessionStorage.getItem(SEEN) === "1") return
      sessionStorage.setItem(SEEN, "1")
    } catch {
      return // without storage, never hold people up
    }
    event.preventDefault()
    const image = stage.current?.querySelector<HTMLElement>(".hero-image")
    const width = image?.offsetWidth ?? window.innerWidth
    const height = image?.offsetHeight ?? 400
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    setCut({ angle: (Math.atan2(height, width) * 180) / Math.PI, length: Math.hypot(width, height), height, reduced })
    timer.current = window.setTimeout(() => navigate(to), reduced ? 200 : 720)
  }

  const className = cut ? (cut.reduced ? " is-fading" : " is-cutting") : ""
  return (
    <div
      ref={stage}
      onClickCapture={onClickCapture}
      className={`katana-stage${className}`}
      style={
        cut
          ? ({ "--cut-angle": `${-cut.angle}deg`, "--cut-length": `${cut.length}px`, "--cut-height": `${cut.height}px` } as CSSProperties)
          : undefined
      }
    >
      {children}
      {cut && !cut.reduced && (
        <div className="katana-overlay" aria-hidden="true">
          <span className="katana-slash"></span>
          {PETALS.map((p, i) => (
            <span
              key={i}
              className="katana-petal"
              style={
                {
                  left: `${100 - p.along}%`,
                  top: `${p.along}%`,
                  "--dx": `${p.dx}px`,
                  "--turn": `${p.turn}deg`,
                  animationDelay: `${0.15 + p.delay}s`,
                } as CSSProperties
              }
            ></span>
          ))}
        </div>
      )}
    </div>
  )
}
