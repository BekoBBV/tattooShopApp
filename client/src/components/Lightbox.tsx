import { ChevronLeft, ChevronRight, X } from "lucide-react"
import { useEffect } from "react"
import type { Image } from "../lib/types"

export type LightboxPhoto = { image: Image; caption: string }

/**
 * A larger view of photos (portfolio, reference photos, message photos), with previous / next and
 * close; the arrow keys and Escape work too.
 */
export function Lightbox({
  photos,
  index,
  label,
  onChange,
  onClose,
}: {
  photos: LightboxPhoto[]
  index: number
  /** What the photos are, for screen readers, e.g. "Kai's work". */
  label: string
  onChange: (index: number) => void
  onClose: () => void
}) {
  const photo = photos[index]
  const go = (step: number) => onChange((index + step + photos.length) % photos.length)
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose()
      if (event.key === "ArrowRight") go(1)
      if (event.key === "ArrowLeft") go(-1)
    }
    document.addEventListener("keydown", onKey)
    document.body.style.overflow = "hidden"
    return () => {
      document.removeEventListener("keydown", onKey)
      document.body.style.overflow = ""
    }
  })
  return (
    <div className="lightbox" role="dialog" aria-modal="true" aria-label={label}>
      <div className="lightbox-top">
        <p>
          {photo.caption} ({index + 1} of {photos.length})
        </p>
        <button type="button" aria-label="Close" onClick={onClose} autoFocus>
          <X className="icon" size={22} aria-hidden="true" />
        </button>
      </div>
      <figure>
        <img src={photo.image.url} alt={photo.caption} />
      </figure>
      {photos.length > 1 && (
        <div className="lightbox-nav">
          <button type="button" aria-label="Previous" onClick={() => go(-1)}>
            <ChevronLeft className="icon" size={22} aria-hidden="true" />
          </button>
          <button type="button" aria-label="Next" onClick={() => go(1)}>
            <ChevronRight className="icon" size={22} aria-hidden="true" />
          </button>
        </div>
      )}
    </div>
  )
}
