import { describe, expect, it } from "vitest"
import { getDraft, takenByOthers, type BookingDraft } from "./bookingDraft"

const draft = (change: Partial<BookingDraft>): BookingDraft => ({ ...getDraft(), ...change })
const oni = { id: "oni", bookable: false }

describe("takenByOthers", () => {
  it("is false while the design can be booked", () => {
    expect(takenByOthers(draft({}), { id: "oni", bookable: true })).toBe(false)
  })

  it("is true when someone else booked it", () => {
    expect(takenByOthers(draft({ code: null }), oni)).toBe(true)
  })

  it("is false for the visitor's own unpaid hold on that design", () => {
    expect(takenByOthers(draft({ code: "AB12-CD", heldFlashId: "oni" }), oni)).toBe(false)
  })

  it("is true when the visitor's earlier booking was for another design", () => {
    expect(takenByOthers(draft({ code: "AB12-CD", heldFlashId: "peony" }), oni)).toBe(true)
  })
})
