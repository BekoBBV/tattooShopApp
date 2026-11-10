import { describe, expect, it } from "vitest"
import { shorten } from "./staffText"

describe("shorten", () => {
  it("keeps short text as it is", () => {
    expect(shorten("Koi on the forearm", 60)).toBe("Koi on the forearm")
  })

  it("cuts long text at a word break and marks the cut", () => {
    const idea = "Test consultation for checking the artist pages; will be cancelled"
    const short = shorten(idea, 60)
    expect(short).toBe("Test consultation for checking the artist pages; will be…")
    expect(short.length).toBeLessThanOrEqual(60)
  })

  it("cuts one very long word where it must", () => {
    expect(shorten("x".repeat(80), 10)).toBe(`${"x".repeat(9)}…`)
  })
})
