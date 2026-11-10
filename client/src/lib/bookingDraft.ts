import { useSyncExternalStore } from "react"
import type { ColourChoice, Placement } from "./types"

/**
 * What the visitor chose so far in the booking steps. Kept for this browser tab (sessionStorage),
 * so going back a step, reloading or coming back from the payment page keeps it.
 */
export type BookingDraft = {
  kind: "flash" | "consultation" | null
  flashId: string | null
  /** The consultation's artist (a flash design brings its own). */
  artistId: string | null
  date: string | null
  time: string | null
  details: {
    customerName: string
    phone: string
    email: string
    /** As typed, "dd.mm.yyyy" */
    dateOfBirth: string
    notes: string
    idea: string
    placement: Placement
    sizeCm: string
    colour: ColourChoice
  }
  /** The booking once it is made (step 3), to pay the deposit and show the confirmation. */
  code: string | null
  phone: string | null
}

const KEY = "blade-and-brush-booking"
const CHANGED = "blade-and-brush-booking-changed"

export const emptyDetails: BookingDraft["details"] = {
  customerName: "",
  phone: "",
  email: "",
  dateOfBirth: "",
  notes: "",
  idea: "",
  placement: "FOREARM",
  sizeCm: "",
  colour: "NOT_SURE",
}

const empty: BookingDraft = {
  kind: null,
  flashId: null,
  artistId: null,
  date: null,
  time: null,
  details: emptyDetails,
  code: null,
  phone: null,
}

let memory: string | null = null
let cachedRaw: string | null | undefined
let cached: BookingDraft = empty

function read(): string | null {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return memory
  }
}

export function getDraft(): BookingDraft {
  const raw = read()
  if (raw !== cachedRaw) {
    cachedRaw = raw
    try {
      cached = raw ? { ...empty, ...(JSON.parse(raw) as BookingDraft) } : empty
    } catch {
      cached = empty
    }
  }
  return cached
}

export function saveDraft(next: BookingDraft) {
  memory = JSON.stringify(next)
  try {
    sessionStorage.setItem(KEY, memory)
  } catch {
    // Storage blocked: kept in memory for this page
  }
  window.dispatchEvent(new Event(CHANGED))
}

export function updateDraft(change: Partial<BookingDraft>) {
  saveDraft({ ...getDraft(), ...change })
}

/** Starts a new booking but keeps the visitor's details for the next one. */
export function startDraft(change: Partial<BookingDraft>) {
  saveDraft({ ...empty, details: getDraft().details, ...change })
}

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGED, onChange)
  return () => window.removeEventListener(CHANGED, onChange)
}

export function useBookingDraft() {
  return useSyncExternalStore(subscribe, getDraft, () => empty)
}
