import { useState, type FocusEvent, type FormEvent } from "react"
import { limits } from "./limits"

/**
 * Form checks that mirror the server's rules, so mistakes show while filling the form
 * instead of after pressing the button. The server still checks everything.
 */
export type FieldErrors = Record<string, string>

/** Same rules as the server's PhoneNumbers: numbers without a country code are Bulgarian. */
export function isPhone(value: string) {
  let s = value.trim().replace(/[\s\-().\\/]/g, "")
  if (s.startsWith("00")) s = "+" + s.slice(2)
  else if (s.startsWith("0")) s = "+359" + s.slice(1)
  else if (s.startsWith("359")) s = "+" + s
  else if (!s.startsWith("+")) s = "+359" + s
  return /^\+[1-9]\d{7,14}$/.test(s)
}

export const isEmail = (value: string) =>
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())

/** Booking codes look like FS-7K3QXM (the "FS-" can be left out). */
export const isBookingCode = (value: string) =>
  /^(FS-?)?[A-HJ-NP-Z2-9]{6}$/.test(value.trim().toUpperCase().replace(/\s/g, ""))

const PHONE_HINT = "Enter a phone number, e.g. 0888 123 456."
const tooLong = (max: number) => `Keep it under ${max} characters.`

/** Only the problems that exist, keyed by the field name the server uses. */
const only = (errors: Record<string, string | false>) =>
  Object.fromEntries(
    Object.entries(errors).filter(([, message]) => message),
  ) as FieldErrors

export const checkBooking = (c: {
  name: string
  phone: string
  email: string
  notes: string
}) =>
  only({
    customerName:
      (!c.name.trim() && "Please enter your name.") ||
      (c.name.length > limits["booking.customerName"] && tooLong(limits["booking.customerName"])),
    phone:
      (!c.phone.trim() && "Please enter your phone number.") ||
      (!isPhone(c.phone) && PHONE_HINT),
    email:
      (c.email.trim() !== "" &&
        !isEmail(c.email) &&
        "That email doesn't look right.") ||
      (c.email.length > limits["booking.email"] && tooLong(limits["booking.email"])),
    notes: c.notes.length > limits["booking.notes"] && tooLong(limits["booking.notes"]),
  })

export const checkReview = (data: FormData) => {
  const name = String(data.get("name") || "")
  const rating = Number(data.get("rating"))
  const text = String(data.get("text") || "")
  return only({
    name: !name.trim() && "Please enter your name.",
    rating:
      !(Number.isInteger(rating) && rating >= 1 && rating <= 5) &&
      "Pick 1 to 5 stars.",
    text: !text.trim() && "Tell us a little about your visit.",
  })
}

export const checkMessage = (data: FormData) => {
  const name = String(data.get("name") || "")
  const contact = String(data.get("contact") || "")
  const text = String(data.get("text") || "")
  return only({
    name: !name.trim() && "Please enter your name.",
    contact:
      (!contact.trim() && "How can we reach you?") ||
      (!isEmail(contact) &&
        !isPhone(contact) &&
        "Enter an email address or a phone number."),
    text: !text.trim() && "Please write a message.",
  })
}

export const checkLookup = (code: string, phone: string) =>
  only({
    code:
      (!code.trim() && "Enter the code from your confirmation.") ||
      (!isBookingCode(code) && "Booking codes look like FS-7K3QXM."),
    phone:
      (!phone.trim() && "Enter the phone number you booked with.") ||
      (!isPhone(phone) && PHONE_HINT),
  })

/**
 * Decides when to show which error: a field's error appears once the field was left (blur)
 * or the form was submitted, and disappears as soon as it is fixed. Server errors
 * (`serverErrors`) win, because they are the final word.
 */
export function useFieldChecks(errors: FieldErrors, serverErrors: FieldErrors = {}) {
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [submitted, setSubmitted] = useState(false)
  // Fields changed since the last submit: their server error (about the old value) is hidden
  const [edited, setEdited] = useState<Record<string, boolean>>({})
  const fieldName = (target: EventTarget) =>
    target instanceof HTMLElement ? target.getAttribute("name") : null
  return {
    errorFor: (field: string): string | undefined =>
      (!edited[field] && serverErrors[field]) ||
      (submitted || touched[field] ? errors[field] : undefined),
    /** Put on the <form>: input events bubble up from every field. */
    onChange: (event: FormEvent<HTMLFormElement>) => {
      const name = fieldName(event.target)
      if (name) setEdited((e) => (e[name] ? e : { ...e, [name]: true }))
    },
    /** Put on the <form>: blur events bubble up from every field. */
    onBlur: (event: FocusEvent<HTMLFormElement>) => {
      // The field that was left (blur events bubble up to the form)
      const name = fieldName(event.target)
      if (name) setTouched((t) => (t[name] ? t : { ...t, [name]: true }))
    },
    /** Call on submit: true if the form may be sent, otherwise focuses the first problem. */
    canSubmit: (form: HTMLFormElement, current: FieldErrors = errors) => {
      setSubmitted(true)
      setEdited({})
      const first = Object.keys(current)[0]
      if (!first) return true
      form.querySelector<HTMLElement>(`[name="${first}"]`)?.focus()
      return false
    },
  }
}

export const checkSignIn = (c: { email: string; password: string }) =>
  only({
    email:
      (!c.email.trim() && "Enter the email you signed up with.") ||
      (!isEmail(c.email) && "That email doesn't look right."),
    password: !c.password && "Enter your password.",
  })

/** Same limits as the server's RegisterRequest. */
export const checkRegister = (c: {
  name: string
  phone: string
  email: string
  password: string
}) =>
  only({
    name:
      (!c.name.trim() && "Please enter your name.") ||
      (c.name.length > limits["account.name"] && tooLong(limits["account.name"])),
    phone:
      (!c.phone.trim() && "Please enter your phone number.") ||
      (!isPhone(c.phone) && PHONE_HINT),
    email:
      (!c.email.trim() && "Please enter your email.") ||
      (!isEmail(c.email) && "That email doesn't look right.") ||
      (c.email.length > limits["account.email"] && tooLong(limits["account.email"])),
    password:
      (c.password.length < limits["account.passwordMin"] && `Use at least ${limits["account.passwordMin"]} characters.`) ||
      (c.password.length > limits["account.password"] && tooLong(limits["account.password"])),
  })
