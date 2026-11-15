/// <reference types="node" />
import { readdirSync, readFileSync } from "node:fs"
import { describe, expect, it } from "vitest"

/**
 * The sample flash drawings live in three places: the files (client/public/images/flash), the
 * server's list that gives them to the designs (DataSeeder.FLASH_PICTURES) and the sample data,
 * which names each design's drawing after its id. This keeps the three in step.
 */
const files = readdirSync(new URL("../../public/images/flash", import.meta.url)).sort()
const seeder = readFileSync(
  new URL("../../../server/src/main/java/com/tattoostudio/config/DataSeeder.java", import.meta.url),
  "utf8",
)
const mock = readFileSync(new URL("../mock/mockApi.ts", import.meta.url), "utf8")

describe("sample flash drawings", () => {
  it("are the files the server hands out, no more and no fewer", () => {
    const listed = [...seeder.matchAll(/"flash\/([\w-]+\.jpg)"/g)].map((m) => m[1]).sort()
    expect(listed).toEqual(files)
  })

  it("include one for every design in the sample data", () => {
    const ids = [...mock.matchAll(/design\("([\w-]+)", "(?:kai|dario|nika)"/g)].map((m) => `${m[1]}.jpg`).sort()
    expect(ids.length).toBe(12)
    expect(ids).toEqual(files)
  })
})
