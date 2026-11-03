import { describe, expect, it } from "vitest"
import {
  checkBooking,
  checkLookup,
  checkMessage,
  checkRegister,
  checkReview,
  checkSignIn,
  isBookingCode,
  isEmail,
  isPhone,
} from "./validation"

const form = (fields: Record<string, string>) => {
  const data = new FormData()
  for (const [name, value] of Object.entries(fields)) data.set(name, value)
  return data
}

describe("phone numbers (same rules as the server's PhoneNumbers)", () => {
  it("accepts the usual Bulgarian and international forms", () => {
    for (const phone of ["0888 123 456", "+359 888 123 456", "00359888123456", "359888123456", "888123456", "(0888) 123-456"])
      expect(isPhone(phone), phone).toBe(true)
  })
  it("rejects things that are not phone numbers", () => {
    for (const phone of ["", "12", "hello", "0888 1", "+0 888 123 456"])
      expect(isPhone(phone), phone).toBe(false)
  })
})

describe("emails and booking codes", () => {
  it("checks emails loosely", () => {
    expect(isEmail("ivan@example.com")).toBe(true)
    expect(isEmail(" ivan@example.com ")).toBe(true)
    expect(isEmail("ivan@example")).toBe(false)
    expect(isEmail("not an email")).toBe(false)
  })
  it("accepts codes with or without the FS- prefix and dash, in any case", () => {
    for (const code of ["FS-7K3QXM", "fs-7k3qxm", "FS7K3QXM", "7K3QXM", " 7k3 qxm "])
      expect(isBookingCode(code), code).toBe(true)
  })
  it("rejects codes with look-alike characters or the wrong length", () => {
    for (const code of ["FS-7K3QX", "FS-7K3QXMM", "FS-0O1I2Z"])
      expect(isBookingCode(code), code).toBe(false)
  })
})

describe("form checks", () => {
  const customer = { name: "Ivan", phone: "0888 123 456", email: "", notes: "" }

  it("a complete booking form has no errors", () => {
    expect(checkBooking(customer)).toEqual({})
  })
  it("reports each booking problem under the server's field name", () => {
    expect(
      checkBooking({ name: " ", phone: "12", email: "nope", notes: "x".repeat(301) }),
    ).toEqual({
      customerName: "Please enter your name.",
      phone: "Enter a phone number, e.g. 0888 123 456.",
      email: "That email doesn't look right.",
      notes: "Keep it under 300 characters.",
    })
  })
  it("email is optional", () => {
    expect(checkBooking({ ...customer, email: "" }).email).toBeUndefined()
  })
  it("reviews need a name, 1–5 stars and text", () => {
    expect(checkReview(form({ name: "Ana", rating: "5", text: "Great" }))).toEqual({})
    expect(Object.keys(checkReview(form({ name: "", rating: "6", text: "" })))).toEqual(["name", "rating", "text"])
  })
  it("messages need an email or a phone number to reply to", () => {
    expect(checkMessage(form({ name: "Ana", contact: "ana@example.com", text: "Hi" }))).toEqual({})
    expect(checkMessage(form({ name: "Ana", contact: "0888 123 456", text: "Hi" }))).toEqual({})
    expect(checkMessage(form({ name: "Ana", contact: "hello", text: "Hi" })).contact).toBe(
      "Enter an email address or a phone number.",
    )
  })
  it("finding a booking needs a code and a phone", () => {
    expect(checkLookup("FS-7K3QXM", "0888 123 456")).toEqual({})
    expect(checkLookup("ABC", "")).toEqual({
      code: "Booking codes look like FS-7K3QXM.",
      phone: "Enter the phone number you booked with.",
    })
  })
})

describe("customer account forms (same limits as the server)", () => {
  it("accepts a complete sign-up", () => {
    expect(
      checkRegister({
        name: "Alex Morgan",
        phone: "0888 123 456",
        email: "alex@example.com",
        password: "12345678",
      }),
    ).toEqual({})
  })

  it("explains every problem in a sign-up", () => {
    expect(
      checkRegister({ name: " ", phone: "12", email: "alex@", password: "short" }),
    ).toEqual({
      name: "Please enter your name.",
      phone: "Enter a phone number, e.g. 0888 123 456.",
      email: "That email doesn't look right.",
      password: "Use at least 8 characters.",
    })
    expect(
      checkRegister({
        name: "x".repeat(61),
        phone: "0888 123 456",
        email: "alex@example.com",
        password: "12345678",
      }),
    ).toEqual({ name: "Keep it under 60 characters." })
  })

  it("needs an email and a password to sign in", () => {
    expect(checkSignIn({ email: "", password: "" })).toEqual({
      email: "Enter the email you signed up with.",
      password: "Enter your password.",
    })
    expect(checkSignIn({ email: "alex@example.com", password: "x" })).toEqual({})
  })
})
