import { Check, X } from "lucide-react"
import { useEffect, useRef, type FocusEvent, type FormEvent, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react"
import { Link } from "react-router"
import { euros, hours, size } from "../lib/format"
import type { Flash, Image } from "../lib/types"

/** An artist's seal: a red frame with their initial (filled red on black backgrounds, see the CSS). */
export function ArtistSeal({ name, px = 32 }: { name: string; px?: 32 | 48 }) {
  return (
    <span className="seal artist-seal" style={{ width: px, height: px, fontSize: px * 0.48 }} aria-hidden="true">
      {name.charAt(0).toUpperCase()}
    </span>
  )
}

/** A picture in a frame, or the empty paper tile until one is uploaded. */
export function Art({ image, alt = "", fit = "contain" }: { image: Image | null | undefined; alt?: string; fit?: "contain" | "cover" }) {
  if (!image) return <div className="flash-art" aria-hidden="true"></div>
  return <img className={`art-image is-${fit}`} src={image.thumbUrl} alt={alt} loading="lazy" />
}

/** One design on a flash sheet: picture, name, the artist's seal, size, time and price. */
export function FlashCard({
  flash,
  byline = true,
  action,
  aboutTime = true,
  filledSeal = false,
  href,
}: {
  flash: Flash
  byline?: boolean
  /** The Book button (or nothing on the home page). */
  action?: ReactNode
  aboutTime?: boolean
  /** The home page's seals are filled red, like the logo. */
  filledSeal?: boolean
  /** Makes the whole card a link (through its title). */
  href?: string
}) {
  const seal = <ArtistSeal name={flash.artistName} />
  return (
    <article className={`flash-card${href ? " is-linked" : ""}`}>
      <div className="flash-frame">
        <Art image={flash.image} alt={flash.name} />
      </div>
      <div className="flash-title-row">
        <h3>
          {href ? (
            <Link className="card-link" to={href}>
              {flash.name}
            </Link>
          ) : (
            flash.name
          )}
        </h3>
        {filledSeal ? <span className="seal-lockup">{seal}</span> : seal}
      </div>
      {byline && <p className="artist-byline">By {flash.artistName}</p>}
      <dl>
        <div>
          <dt>Size</dt>
          <dd>{size(flash.widthCm, flash.heightCm)}</dd>
        </div>
        <div>
          <dt>Time</dt>
          <dd>{aboutTime ? `About ${hours(flash.durationMinutes)}` : hours(flash.durationMinutes)}</dd>
        </div>
        <div>
          <dt>Price</dt>
          <dd>{euros(flash.price)}</dd>
        </div>
      </dl>
      {action}
    </article>
  )
}

/** The Book button under a flash design: straight to the booking steps with it chosen. */
export function BookFlashButton({ flash }: { flash: Flash }) {
  return (
    <Link className="button button-primary" to={`/book/choose?flash=${flash.id}`} aria-label={`Book ${flash.name}`}>
      Book
    </Link>
  )
}

/** The artist's name written top to bottom, one upright letter under the other. */
export function VerticalName({ name }: { name: string }) {
  return (
    <p className="vertical-name" aria-label={name}>
      {[...name].map((letter, i) => (
        <span key={i} aria-hidden="true">
          {letter}
        </span>
      ))}
    </p>
  )
}

/** A hanging scroll: wooden rods, the vertical name, the signature piece and the seal. */
export function HangingScroll({
  name,
  signature,
  variant = "normal",
}: {
  name: string
  signature: Image | null
  variant?: "mini" | "normal" | "wide"
}) {
  const className = `hanging-scroll${variant === "mini" ? " is-mini" : variant === "wide" ? " is-wide" : ""}`
  return (
    <div className={className} aria-hidden={variant === "mini" ? true : undefined}>
      <div className="scroll-rod">
        <i></i>
        <i></i>
      </div>
      <div className="scroll-paper">
        <VerticalName name={name} />
        <div className="scroll-signature">
          <Art image={signature} alt={`A signature piece by ${name}`} />
        </div>
        {variant !== "mini" && <ArtistSeal name={name} px={48} />}
      </div>
      <div className="scroll-rod">
        <i></i>
        <i></i>
      </div>
    </div>
  )
}

const STEPS = [
  { label: "Choose", to: "/book/choose" },
  { label: "Time", to: "/book/time" },
  { label: "Details", to: "/book/details" },
  { label: "Deposit", to: "/book/deposit" },
]

/**
 * The booking steps. Finished steps link back; on phones only the numbers and the current step's
 * name show (the CSS). Consultations have no deposit step.
 */
export function StepBar({ current, withDeposit = true, locked = false }: { current: 1 | 2 | 3 | 4; withDeposit?: boolean; locked?: boolean }) {
  const steps = withDeposit ? STEPS : STEPS.slice(0, 3)
  return (
    <nav className="wrap booking-steps-nav" aria-label="Booking progress">
      <ol>
        {steps.map((step, i) => {
          const n = i + 1
          if (n < current && !locked) {
            return (
              <li key={step.label} className="is-finished">
                <Link to={step.to} aria-label={`Back to ${step.label}`}>
                  <span className="step-square">
                    <Check className="icon" size={18} aria-hidden="true" />
                  </span>
                  <strong>{step.label}</strong>
                </Link>
              </li>
            )
          }
          return (
            <li key={step.label} className={n === current ? "is-current" : n < current ? "is-finished" : ""}>
              <div aria-current={n === current ? "step" : undefined}>
                <span className="step-square">{n < current ? <Check className="icon" size={18} aria-hidden="true" /> : n}</span>
                <strong>{step.label}</strong>
              </div>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

/**
 * A dialog: a paper slip over the dimmed page (a bottom sheet on phones). Focus moves into it and
 * back to where it was when it closes; Escape closes it.
 */
export function Dialog({
  title,
  titleId,
  onClose,
  children,
  as = "div",
  onSubmit,
  onChange,
  onBlur,
  closeButton = false,
}: {
  title: string
  titleId: string
  onClose: () => void
  children: ReactNode
  as?: "div" | "form"
  onSubmit?: (event: FormEvent<HTMLFormElement>) => void
  /** For the field checks of a form dialog (see useFieldChecks). */
  onChange?: (event: FormEvent<HTMLFormElement>) => void
  onBlur?: (event: FocusEvent<HTMLFormElement>) => void
  closeButton?: boolean
}) {
  const ref = useRef<HTMLElement | null>(null)
  // The latest onClose, so the effect below can set up once when the dialog opens
  const close = useRef(onClose)
  useEffect(() => {
    close.current = onClose
  })
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    // A data-autofocus element first (the safe choice in a confirmation), else the first field or button
    const first =
      ref.current?.querySelector<HTMLElement>("[data-autofocus]") ??
      ref.current?.querySelector<HTMLElement>("input, textarea, select, button:not(.dialog-close)")
    first?.focus()
    const onKey = (event: KeyboardEvent) => {
      // Only the top dialog reacts (a confirmation can open over another dialog)
      const open = document.querySelectorAll(".dialog-backdrop")
      if (open[open.length - 1] !== ref.current?.parentElement) return
      if (event.key === "Escape") close.current()
      if (event.key === "Tab" && ref.current) {
        // Keep the focus inside the dialog
        const items = [...ref.current.querySelectorAll<HTMLElement>("a, button, input, textarea, select")].filter((el) => !el.hasAttribute("disabled"))
        if (items.length === 0) return
        const [firstItem, lastItem] = [items[0], items[items.length - 1]]
        if (event.shiftKey && document.activeElement === firstItem) {
          event.preventDefault()
          lastItem.focus()
        } else if (!event.shiftKey && document.activeElement === lastItem) {
          event.preventDefault()
          firstItem.focus()
        }
      }
    }
    document.addEventListener("keydown", onKey)
    document.body.style.overflow = "hidden"
    return () => {
      document.removeEventListener("keydown", onKey)
      // The page scrolls again once no dialog is left (the closed one is already gone from the page here)
      if (document.querySelectorAll(".dialog-backdrop").length === 0) document.body.style.overflow = ""
      previous?.focus()
    }
  }, [])
  const content = (
    <>
      {closeButton && (
        <button className="dialog-close" type="button" aria-label="Close" onClick={onClose}>
          <X className="icon" size={20} aria-hidden="true" />
        </button>
      )}
      <h2 id={titleId}>{title}</h2>
      {children}
    </>
  )
  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      {as === "form" ? (
        <form
          ref={(el) => {
            ref.current = el
          }}
          className="slip on-paper dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          onSubmit={onSubmit}
          onChange={onChange}
          onBlur={onBlur}
          noValidate
        >
          {content}
        </form>
      ) : (
        <div
          ref={(el) => {
            ref.current = el
          }}
          className="slip on-paper dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
        >
          {content}
        </div>
      )}
    </div>
  )
}

type FieldProps = { label: string; error?: string; helper?: string; full?: boolean }

/** A labelled text field with its helper or error text below. */
export function TextField({ label, error, helper, full, ...input }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const describedBy = input.name ? `${input.name}-note` : undefined
  return (
    <label className={`field${error ? " has-error" : ""}${full ? " full" : ""}`}>
      {label}
      <input {...input} aria-invalid={error ? true : undefined} aria-describedby={error || helper ? describedBy : undefined} />
      {error ? (
        <span className="field-error" id={describedBy}>
          {error}
        </span>
      ) : (
        helper && (
          <span className="helper" id={describedBy}>
            {helper}
          </span>
        )
      )}
    </label>
  )
}

export function TextAreaField({ label, error, helper, full, ...area }: FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const describedBy = area.name ? `${area.name}-note` : undefined
  return (
    <label className={`field${error ? " has-error" : ""}${full ? " full" : ""}`}>
      {label}
      <textarea {...area} aria-invalid={error ? true : undefined} aria-describedby={error || helper ? describedBy : undefined} />
      {error ? (
        <span className="field-error" id={describedBy}>
          {error}
        </span>
      ) : (
        helper && (
          <span className="helper" id={describedBy}>
            {helper}
          </span>
        )
      )}
    </label>
  )
}

export function SelectField({
  label,
  error,
  options,
  ...select
}: FieldProps & SelectHTMLAttributes<HTMLSelectElement> & { options: { value: string; label: string }[] }) {
  return (
    <label className={`field${error ? " has-error" : ""}`}>
      {label}
      <select {...select} aria-invalid={error ? true : undefined}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {error && <span className="field-error">{error}</span>}
    </label>
  )
}

/** A consent tick box drawn like the design (a real checkbox underneath, for keyboards and screen readers). */
export function ConsentCheck({
  name,
  checked,
  onChange,
  children,
  error,
}: {
  name: string
  checked: boolean
  onChange: (checked: boolean) => void
  children: ReactNode
  error?: string
}) {
  return (
    <>
      <label className="consent-check">
        <input type="checkbox" className="visually-hidden" name={name} checked={checked} onChange={(e) => onChange(e.target.checked)} />
        <span className={`check-mark${checked ? " is-on" : ""}`} aria-hidden="true">
          {checked && <Check className="icon" size={16} aria-hidden="true" />}
        </span>
        <span>{children}</span>
      </label>
      {error && <p className="field-error consent-error">{error}</p>}
    </>
  )
}

/** The password rules under a new password, each ticked off as it is met. */
export function PasswordRules({ rules }: { rules: { label: string; met: boolean }[] }) {
  return (
    <ul className="password-rules" aria-label="Password requirements">
      {rules.map((rule) => (
        <li key={rule.label} className={rule.met ? "is-passed" : ""}>
          <span className="rule-box">{rule.met && <Check className="icon" size={14} aria-hidden="true" />}</span>
          {rule.label}
          {rule.met && <span className="visually-hidden"> (done)</span>}
        </li>
      ))}
    </ul>
  )
}

/** A problem with the whole form or page, e.g. the time was just taken. */
export function FormError({ children }: { children: ReactNode }) {
  if (!children) return null
  return (
    <p className="form-error" role="alert">
      {children}
    </p>
  )
}

/** A red rotated stamp ("Deposit paid"), dashed while something is still waiting. */
export function Stamp({ children, tone = "red" }: { children: ReactNode; tone?: "red" | "waiting" | "done" }) {
  return <span className={`stamp${tone === "waiting" ? " is-waiting" : tone === "done" ? " is-done" : ""}`}>{children}</span>
}
