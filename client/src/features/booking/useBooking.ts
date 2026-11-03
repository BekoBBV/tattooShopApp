import { createContext, useContext } from "react"

type DraftSlot = {
  date: string
  time: string
  startsAt: string
  freeArtists: number
}

type DraftCustomer = {
  name: string
  email: string
  phone: string
  notes: string
}

export type BookingDraft = {
  serviceId: string
  artistId: string
  slot: DraftSlot | null
  customer: DraftCustomer
}

export type BookingContextValue = {
  draft: BookingDraft
  /** A patch, or a function of the current draft (for changes based on the latest values) */
  update: (
    patch: Partial<BookingDraft> | ((current: BookingDraft) => Partial<BookingDraft>),
  ) => void
  reset: () => void
}

/** Shared by BookingProvider (BookingContext.tsx) and the booking pages. */
export const BookingContext = createContext<BookingContextValue | null>(null)

export function useBooking() {
  const value = useContext(BookingContext)
  if (!value) throw new Error("useBooking must be used inside BookingProvider")
  return value
}
