import { useState, type FocusEvent, type FormEvent } from "react"
import { ageOn, parseTypedDate } from "./format"
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

export const isEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())

/** Booking codes look like K7Q2-M9 (the dash and spaces may be left out, any case). */
export const isBookingCode = (value: string) =>
  /^[A-HJ-NP-Z2-9]{6}$/.test(value.trim().toUpperCase().replace(/[\s-]/g, ""))

const PHONE_HINT = "Enter a phone number, e.g. 0888 123 456."
const tooLong = (max: number) => `Keep it under ${max} characters.`

/** Only the problems that exist, keyed by the field name the server uses. */
const only = (errors: Record<string, string | false | undefined>) =>
  Object.fromEntries(Object.entries(errors).filter(([, message]) => message)) as FieldErrors

/** Date of birth as typed ("dd.mm.yyyy"): a real date, and 18 or older on `onDate`. */
export function birthDateProblem(typed: string, onDate: string, what = "get tattooed") {
  if (!typed.trim()) return "Please enter your date of birth."
  const date = parseTypedDate(typed)
  if (!date || date < "1900-01-01") return "Write it like 14.06.1998."
  if (ageOn(date, onDate) < 18) return `You need to be 18 or older to ${what}.`
  return undefined
}

export type DetailsForm = {
  customerName: string
  phone: string
  email: string
  dateOfBirth: string
  notes: string
  idea: string
  sizeCm: string
  adult: boolean
  consentForm: boolean
  depositPolicy: boolean
}

/** The customer details step; consultations also need the idea, flash the deposit policy. */
export const checkDetails = (d: DetailsForm, kind: "flash" | "consultation", appointmentDate: string) =>
  only({
    customerName:
      (!d.customerName.trim() && "Please enter your full name.") ||
      (d.customerName.length > limits["booking.customerName"] && tooLong(limits["booking.customerName"])),
    phone: (!d.phone.trim() && "Please enter your phone number.") || (!isPhone(d.phone) && PHONE_HINT),
    email:
      (!d.email.trim() && "Please enter your email, for the confirmation.") ||
      (!isEmail(d.email) && "That email doesn't look right.") ||
      (d.email.length > limits["booking.email"] && tooLong(limits["booking.email"])),
    dateOfBirth: birthDateProblem(d.dateOfBirth, appointmentDate),
    notes: d.notes.length > limits["booking.notes"] && tooLong(limits["booking.notes"]),
    idea:
      kind === "consultation" &&
      ((!d.idea.trim() && "Tell your artist a little about your idea.") ||
        (d.idea.length > limits["consultation.idea"] && tooLong(limits["consultation.idea"]))),
    sizeCm:
      kind === "consultation" &&
      d.sizeCm.trim() !== "" &&
      !(Number.isInteger(Number(d.sizeCm)) && Number(d.sizeCm) >= 1 && Number(d.sizeCm) <= 200) &&
      "Enter a size from 1 to 200 cm, or leave it empty.",
    adult: !d.adult && "Please confirm that you are 18 or older.",
    consentForm: !d.consentForm && "Please read and agree to the consent form.",
    depositPolicy: kind === "flash" && !d.depositPolicy && "Please confirm the deposit policy.",
  })

export const checkReview = (c: { rating: number; text: string }) =>
  only({
    rating: !(Number.isInteger(c.rating) && c.rating >= 1 && c.rating <= 5) && "Pick 1 to 5.",
    text:
      (!c.text.trim() && "Tell us a little about your tattoo.") ||
      (c.text.length > limits["review.text"] && tooLong(limits["review.text"])),
  })

export const checkMessage = (c: { name: string; contact: string; text: string }, photos = 0) =>
  only({
    name: !c.name.trim() && "Please enter your name.",
    contact:
      (!c.contact.trim() && "How can we reach you?") ||
      (!isEmail(c.contact) && !isPhone(c.contact) && "Enter an email address or a phone number."),
    text: !c.text.trim() && "Please write a message.",
    photos: photos > 5 && "You can attach up to 5 photos.",
  })

export const checkLookup = (code: string, phone: string) =>
  only({
    code:
      (!code.trim() && "Enter the code from your confirmation.") ||
      (!isBookingCode(code) && "Booking codes look like K7Q2-M9."),
    phone: (!phone.trim() && "Enter the phone number you booked with.") || (!isPhone(phone) && PHONE_HINT),
  })

/** The password rules shown on the sign-up form (the server itself needs 8 or more characters). */
export const passwordRules = (password: string) => [
  { label: `At least ${limits["account.passwordMin"]} characters`, met: password.length >= limits["account.passwordMin"] },
  { label: "One uppercase letter", met: /[A-Z]/.test(password) },
  { label: "One number", met: /\d/.test(password) },
]

export const checkSignIn = (c: { email: string; password: string }) =>
  only({
    email: (!c.email.trim() && "Enter the email you signed up with.") || (!isEmail(c.email) && "That email doesn't look right."),
    password: !c.password && "Enter your password.",
  })

/** An account's name, phone and email (sign-up and Edit details). */
const accountFields = (c: { name: string; phone: string; email: string }) => ({
  name:
    (!c.name.trim() && "Please enter your name.") ||
    (c.name.length > limits["account.name"] && tooLong(limits["account.name"])),
  phone: (!c.phone.trim() && "Please enter your phone number.") || (!isPhone(c.phone) && PHONE_HINT),
  email:
    (!c.email.trim() && "Please enter your email.") ||
    (!isEmail(c.email) && "That email doesn't look right.") ||
    (c.email.length > limits["account.email"] && tooLong(limits["account.email"])),
})

/** Same limits as the server's UpdateAccountRequest. */
export const checkAccountDetails = (c: { name: string; phone: string; email: string }) => only(accountFields(c))

/** Same limits as the server's RegisterRequest. */
export const checkRegister = (
  c: { name: string; phone: string; email: string; dateOfBirth: string; password: string },
  today: string,
) =>
  only({
    ...accountFields(c),
    dateOfBirth: birthDateProblem(c.dateOfBirth, today, "create an account"),
    password:
      (passwordRules(c.password).some((r) => !r.met) && "Your password needs all three things listed below it.") ||
      (c.password.length > limits["account.password"] && tooLong(limits["account.password"])),
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
  const fieldName = (target: EventTarget) => (target instanceof HTMLElement ? target.getAttribute("name") : null)
  return {
    errorFor: (field: string): string | undefined =>
      (!edited[field] && serverErrors[field]) || (submitted || touched[field] ? errors[field] : undefined),
    /** Put on the <form>: input events bubble up from every field. */
    onChange: (event: FormEvent<HTMLFormElement>) => {
      const name = fieldName(event.target)
      if (name) setEdited((e) => (e[name] ? e : { ...e, [name]: true }))
    },
    /** Put on the <form>: blur events bubble up from every field. */
    onBlur: (event: FocusEvent<HTMLFormElement>) => {
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
