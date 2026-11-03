import { describe, expect, it } from "vitest"
import { addDays, formatDay, initials, mondayOf, plural } from "./staffUtils"

describe("dates (YYYY-MM-DD, shop time)", () => {
  it("adds days across month and year ends", () => {
    expect(addDays("2026-10-31", 1)).toBe("2026-11-01")
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01")
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28")
  })
  it("ignores daylight-saving changes (25 Oct 2026 in Sofia)", () => {
    expect(addDays("2026-10-24", 2)).toBe("2026-10-26")
  })
  it("finds the Monday of a week", () => {
    expect(mondayOf("2026-10-19")).toBe("2026-10-19") // Monday
    expect(mondayOf("2026-10-25")).toBe("2026-10-19") // Sunday belongs to the week before
    expect(mondayOf("2026-11-01")).toBe("2026-10-26")
  })
  it("shows dates the short way", () => {
    expect(formatDay("2026-10-20")).toBe("Tue 20 Oct")
  })
})

describe("text helpers", () => {
  it("pluralises counts", () => {
    expect(plural(1, "booking")).toBe("1 booking")
    expect(plural(0, "booking")).toBe("0 bookings")
    expect(plural(2, "done booking")).toBe("2 done bookings")
  })
  it("makes avatar initials from names and usernames", () => {
    expect(initials("Nia Banks")).toBe("NB")
    expect(initials("nia")).toBe("N")
    expect(initials("qa-admin")).toBe("QA")
  })
})
