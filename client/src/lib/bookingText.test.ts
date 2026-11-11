import { describe, expect, it } from "vitest"
import { summaryRows } from "./bookingText"

const koi = { widthCm: 8, heightCm: 12, durationMinutes: 150, price: 220, deposit: 30 }
const row = (rows: ReturnType<typeof summaryRows>, label: string) => rows.find((r) => r.label === label)?.value

describe("summaryRows", () => {
  it("shows a flash design's size, length and money, with the rest to pay in the studio", () => {
    const rows = summaryRows({ kind: "flash", flash: koi, artistName: "Kai", date: null, time: null })
    expect(rows.map((r) => r.label)).toEqual(["Artist", "Size", "Length", "When", "Price", "Deposit now", "Pay in the studio"])
    expect(row(rows, "Size")).toBe("8 × 12 cm")
    expect(row(rows, "Length")).toBe("About 2.5 hours")
    expect(row(rows, "When")).toBe("Not chosen yet")
    expect(row(rows, "Price")).toBe("€220")
    expect(row(rows, "Deposit now")).toBe("€30")
    expect(row(rows, "Pay in the studio")).toBe("€190")
    expect(rows.at(-1)?.total).toBe(true)
  })

  it("shows the day and time once both are chosen", () => {
    const rows = summaryRows({ kind: "flash", flash: koi, artistName: "Kai", date: "2026-11-11", time: "15:00" })
    expect(row(rows, "When")).toBe("Wednesday, 11 November 2026, 15:00")
  })

  it("keeps to what is known while the design is still loading", () => {
    const rows = summaryRows({ kind: "flash", flash: null, artistName: null, date: null, time: null })
    expect(rows.map((r) => r.label)).toEqual(["When"])
  })

  it("shows a consultation as 30 minutes and free", () => {
    const rows = summaryRows({ kind: "consultation", flash: null, artistName: "Nika", date: "2026-11-12", time: "11:30" })
    expect(rows.map((r) => r.label)).toEqual(["Artist", "Length", "When", "Price"])
    expect(row(rows, "Length")).toBe("30 minutes")
    expect(row(rows, "Price")).toBe("Free")
  })
})
