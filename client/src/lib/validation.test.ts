import { describe, expect, it } from "vitest"
import { ageOn, countdown, euros, hours, longDate, parseTypedDate } from "./format"
import {
  birthDateProblem,
  checkDetails,
  checkLookup,
  checkMessage,
  checkRegister,
  checkReview,
  checkSignIn,
  isBookingCode,
  isEmail,
  isPhone,
  passwordRules,
  type DetailsForm,
} from "./validation"

describe("phone numbers (same rules as the server's PhoneNumbers)", () => {
  it("accepts the usual Bulgarian and international forms", () => {
    for (const phone of ["0888 123 456", "+359 888 123 456", "00359888123456", "359888123456", "888123456", "(0888) 123-456"])
      expect(isPhone(phone), phone).toBe(true)
  })
  it("rejects things that are not phone numbers", () => {
    for (const phone of ["", "12", "hello", "0888 1", "+0 888 123 456"]) expect(isPhone(phone), phone).toBe(false)
  })
})

describe("emails and booking codes", () => {
  it("checks emails loosely", () => {
    expect(isEmail("mira@example.com")).toBe(true)
    expect(isEmail(" mira@example.com ")).toBe(true)
    expect(isEmail("mira@example")).toBe(false)
    expect(isEmail("not an email")).toBe(false)
  })
  it("accepts codes like K7Q2-M9 with or without the dash, in any case", () => {
    for (const code of ["K7Q2-M9", "k7q2-m9", "K7Q2M9", " k7q2 m9 "]) expect(isBookingCode(code), code).toBe(true)
  })
  it("rejects codes with look-alike characters or the wrong length", () => {
    for (const code of ["K7Q2-M", "K7Q2-M99", "O1I0-L2", "FS-7K3QXM"]) expect(isBookingCode(code), code).toBe(false)
  })
})

describe("dates of birth (18 or older on the day)", () => {
  it("reads what people type", () => {
    expect(parseTypedDate("14.06.1998")).toBe("1998-06-14")
    expect(parseTypedDate("4/6/1998")).toBe("1998-06-04")
    expect(parseTypedDate("31.02.1998")).toBeNull()
    expect(parseTypedDate("1998-06-14")).toBeNull()
  })
  it("counts whole years up to the appointment", () => {
    expect(ageOn("2008-11-20", "2026-11-19")).toBe(17)
    expect(ageOn("2008-11-20", "2026-11-20")).toBe(18)
  })
  it("explains each problem", () => {
    expect(birthDateProblem("", "2026-11-20")).toBe("Please enter your date of birth.")
    expect(birthDateProblem("14 June 1998", "2026-11-20")).toBe("Write it like 14.06.1998.")
    expect(birthDateProblem("21.11.2008", "2026-11-20")).toBe("You need to be 18 or older to get tattooed.")
    expect(birthDateProblem("20.11.2008", "2026-11-20")).toBeUndefined()
  })
})

describe("the booking details step", () => {
  const complete: DetailsForm = {
    customerName: "Mira Petrova",
    phone: "0888 123 456",
    email: "mira@example.com",
    dateOfBirth: "12.03.1998",
    notes: "",
    idea: "A koi swimming up through waves",
    sizeCm: "18",
    adult: true,
    consentForm: true,
    depositPolicy: true,
  }
  it("a complete form has no errors", () => {
    expect(checkDetails(complete, "flash", "2026-11-20")).toEqual({})
    expect(checkDetails(complete, "consultation", "2026-11-20")).toEqual({})
  })
  it("reports each problem under the server's field name", () => {
    const errors = checkDetails(
      { ...complete, customerName: " ", phone: "12", email: "", adult: false, consentForm: false, depositPolicy: false },
      "flash",
      "2026-11-20",
    )
    expect(Object.keys(errors).sort()).toEqual(["adult", "consentForm", "customerName", "depositPolicy", "email", "phone"])
  })
  it("only flash bookings need the deposit policy, only consultations the idea", () => {
    expect(checkDetails({ ...complete, depositPolicy: false, idea: "" }, "consultation", "2026-11-20")).toEqual({ idea: expect.any(String) })
    expect(checkDetails({ ...complete, depositPolicy: false, idea: "" }, "flash", "2026-11-20")).toEqual({ depositPolicy: expect.any(String) })
  })
  it("the size is optional, but 1 to 200 cm when given", () => {
    expect(checkDetails({ ...complete, sizeCm: "" }, "consultation", "2026-11-20")).toEqual({})
    expect(checkDetails({ ...complete, sizeCm: "0" }, "consultation", "2026-11-20").sizeCm).toBeTruthy()
    expect(checkDetails({ ...complete, sizeCm: "12.5" }, "consultation", "2026-11-20").sizeCm).toBeTruthy()
  })
})

describe("other forms", () => {
  it("reviews need 1 to 5 and a few words", () => {
    expect(checkReview({ rating: 5, text: "Bright and solid" })).toEqual({})
    expect(Object.keys(checkReview({ rating: 0, text: " " })).sort()).toEqual(["rating", "text"])
  })
  it("messages need an email or a phone number to reply to, and at most 5 photos", () => {
    expect(checkMessage({ name: "Vera", contact: "vera@example.com", text: "Hi" })).toEqual({})
    expect(checkMessage({ name: "Vera", contact: "0888 123 456", text: "Hi" })).toEqual({})
    expect(checkMessage({ name: "Vera", contact: "somewhere", text: "Hi" }).contact).toBeTruthy()
    expect(checkMessage({ name: "Vera", contact: "vera@example.com", text: "Hi" }, 6).photos).toBeTruthy()
  })
  it("finding a booking needs a code and a phone", () => {
    expect(checkLookup("K7Q2-M9", "0888 123 456")).toEqual({})
    expect(Object.keys(checkLookup("", "")).sort()).toEqual(["code", "phone"])
    expect(checkLookup("FS-7K3QXM", "0888 123 456").code).toBe("Booking codes look like K7Q2-M9.")
  })
  it("signing in needs an email and a password", () => {
    expect(checkSignIn({ email: "mira@example.com", password: "x" })).toEqual({})
    expect(Object.keys(checkSignIn({ email: "", password: "" })).sort()).toEqual(["email", "password"])
  })
  it("signing up needs every field, 18+ and a password with all three rules", () => {
    const ok = { name: "Mira", phone: "0888 123 456", email: "mira@example.com", dateOfBirth: "12.03.1998", password: "Koimaple2026" }
    expect(checkRegister(ok, "2026-11-20")).toEqual({})
    expect(checkRegister({ ...ok, password: "koimaple2026" }, "2026-11-20").password).toBeTruthy() // no uppercase letter
    expect(checkRegister({ ...ok, dateOfBirth: "01.01.2010" }, "2026-11-20").dateOfBirth).toBe(
      "You need to be 18 or older to create an account.",
    )
    expect(passwordRules("Ab1").map((r) => r.met)).toEqual([false, true, true])
  })
})

describe("formatting", () => {
  it("writes prices, times and dates like the design", () => {
    expect(euros(180)).toBe("€180")
    expect(euros(28.5)).toBe("€28.50")
    expect(hours(60)).toBe("1 hour")
    expect(hours(90)).toBe("1.5 hours")
    expect(hours(30)).toBe("30 minutes")
    expect(longDate("2026-11-05")).toBe("Thursday, 5 November 2026")
  })
  it("counts down the deposit hold", () => {
    const now = Date.UTC(2026, 10, 5, 12, 0, 0)
    expect(countdown(new Date(now + (14 * 60 + 32) * 1000).toISOString(), now)).toBe("14:32")
    expect(countdown(new Date(now - 1000).toISOString(), now)).toBeNull()
  })
})
