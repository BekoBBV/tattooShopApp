import { useEffect, useState } from "react"

/** One page of a list from the API (pages count from 0). */
export type Page<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

/**
 * The state behind a searchable, sortable, paged list: the search text (sent 300 ms after the
 * last key press), extra filters, the sort ("price,desc") and the page. Changing anything but the
 * page goes back to the first page. `query` is the matching query string for the API.
 */
export function useListQuery({
  sort: initialSort,
  size = 10,
  filters: initialFilters = {},
}: {
  sort: string
  size?: number
  filters?: Record<string, string>
}) {
  const [q, setQText] = useState("")
  const [sentQ, setSentQ] = useState("")
  const [sort, setSortValue] = useState(initialSort)
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState(initialFilters)
  useEffect(() => {
    const id = window.setTimeout(() => setSentQ(q.trim()), 300)
    return () => window.clearTimeout(id)
  }, [q])
  const query = new URLSearchParams({ page: String(page), size: String(size), sort })
  if (sentQ) query.set("q", sentQ)
  for (const [name, value] of Object.entries(filters)) if (value) query.set(name, value)
  return {
    q,
    sort,
    page,
    filters,
    query: query.toString(),
    setQ: (value: string) => {
      setQText(value)
      setPage(0)
    },
    setSort: (value: string) => {
      setSortValue(value)
      setPage(0)
    },
    setFilter: (name: string, value: string) => {
      setFilters((current) => ({ ...current, [name]: value }))
      setPage(0)
    },
    setPage,
  }
}

export type ListQueryState = ReturnType<typeof useListQuery>
