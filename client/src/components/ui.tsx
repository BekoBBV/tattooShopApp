import type {
  ButtonHTMLAttributes,
  ElementType,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react"
import { useId } from "react"
import { Link, type LinkProps } from "react-router"
import { LoaderCircle } from "lucide-react"

export function Heading({
  level = 2,
  className = "",
  children,
}: {
  level?: 1 | 2 | 3 | 4
  className?: string
  children: ReactNode
}) {
  const Tag = `h${level}` as ElementType
  return (
    <Tag
      className={`font-display uppercase leading-[0.92] tracking-[-0.025em] ${className}`}
    >
      {children}
    </Tag>
  )
}

export function Button({
  className = "",
  variant = "primary",
  loading,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger"
  loading?: boolean
}) {
  return (
    <button
      {...props}
      className={`button button-${variant} ${className}`}
      disabled={loading || props.disabled}
    >
      {loading ? (
        <LoaderCircle className="size-4 animate-spin" aria-hidden="true" />
      ) : null}
      {children}
    </button>
  )
}

export function BareButton({
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button className={className} {...props} />
}

export function AppLink({
  className = "",
  variant,
  ...props
}: LinkProps & { variant?: "primary" | "secondary" | "ghost" }) {
  return (
    <Link
      className={`${variant ? `button button-${variant}` : ""} ${className}`}
      {...props}
    />
  )
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  error?: string
}

/** The error under a field, linked to it so screen readers read it with the field. */
function FieldError({ id, error }: { id: string; error?: string }) {
  return error ? (
    <small className="field-error" id={id}>
      {error}
    </small>
  ) : null
}

export function Input({ label, error, ...props }: InputProps) {
  const errorId = useId()
  return (
    <label className="field">
      <span>{label}</span>
      <input
        {...props}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
      />
      <FieldError id={errorId} error={error} />
    </label>
  )
}

export function Select({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & {
  label: string
  children: ReactNode
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <select {...props}>{children}</select>
    </label>
  )
}

export function Textarea({
  label,
  error,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string
  error?: string
}) {
  const errorId = useId()
  return (
    <label className="field">
      <span>{label}</span>
      <textarea
        {...props}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
      />
      <FieldError id={errorId} error={error} />
    </label>
  )
}

export function Checkbox({
  label,
  className = "",
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: ReactNode }) {
  return (
    <label className={className}>
      <input type="checkbox" {...props} />
      {label}
    </label>
  )
}

export function TimeInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input type="time" {...props} />
}

export function Eyebrow({ children }: { children: ReactNode }) {
  return (
    <p className="eyebrow">
      <span aria-hidden="true" />
      {children}
    </p>
  )
}

export function StatusBadge({ status }: { status: string }) {
  const labels: Record<string, string> = {
    BOOKED: "Confirmed",
    DONE: "Completed",
    CANCELLED: "Cancelled",
    NO_SHOW: "No-show",
  }
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      {labels[status] ?? status.replace(/_/g, " ")}
    </span>
  )
}
