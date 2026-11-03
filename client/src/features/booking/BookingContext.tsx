import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"
import {
  BookingContext,
  type BookingContextValue,
  type BookingDraft,
} from "./useBooking"

const emptyDraft: BookingDraft = {
  serviceId: "",
  artistId: "",
  slot: null,
  customer: { name: "", email: "", phone: "", notes: "" },
}

export function BookingProvider({ children }: { children: ReactNode }) {
  const [draft, setDraft] = useState<BookingDraft>(() => {
    try {
      const saved = sessionStorage.getItem("tattoo-studio-booking")
      return saved
        ? { ...emptyDraft, ...JSON.parse(saved), customer: emptyDraft.customer }
        : emptyDraft
    } catch {
      return emptyDraft
    }
  })

  useEffect(() => {
    try {
      sessionStorage.setItem(
        "tattoo-studio-booking",
        JSON.stringify({ ...draft, customer: emptyDraft.customer }),
      )
    } catch {
      // Storage blocked (e.g. some private modes): the draft just won't survive a refresh
    }
  }, [draft])

  // Stable functions, so pages can list them as effect dependencies without re-running effects
  const update = useCallback(
    (patch: Partial<BookingDraft> | ((current: BookingDraft) => Partial<BookingDraft>)) =>
      setDraft((current) => ({
        ...current,
        ...(typeof patch === "function" ? patch(current) : patch),
      })),
    [],
  )
  const reset = useCallback(() => setDraft(emptyDraft), [])
  const value = useMemo<BookingContextValue>(
    () => ({ draft, update, reset }),
    [draft, update, reset],
  )

  return (
    <BookingContext.Provider value={value}>{children}</BookingContext.Provider>
  )
}
