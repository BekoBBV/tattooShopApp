import type { ReactNode } from "react"
import { ChevronLeft, ChevronRight, Search } from "lucide-react"
import type { ListQueryState, Page } from "../lib/paging"
import { Button, Select } from "./ui"
import { limits } from "../lib/limits"

/**
 * The bar above every list: a search box, the list's own filters (children) and the sort order.
 * All of it drives one `useListQuery` state.
 */
export function ListToolbar({
  list,
  searchLabel = "Search",
  placeholder,
  sorts,
  children,
}: {
  list: ListQueryState
  searchLabel?: string
  placeholder?: string
  /** [value sent to the API, label], e.g. ["price,asc", "Price: low to high"] */
  sorts: [string, string][]
  children?: ReactNode
}) {
  return (
    <div className="list-toolbar" role="search">
      <label className="field list-search">
        <span>{searchLabel}</span>
        <span className="list-search-box">
          <Search aria-hidden="true" />
          <input
            type="search"
            value={list.q}
            maxLength={limits["search.q"]}
            placeholder={placeholder}
            onChange={(e) => list.setQ(e.target.value)}
          />
        </span>
      </label>
      {children}
      <Select label="Sort by" value={list.sort} onChange={(e) => list.setSort(e.target.value)}>
        {sorts.map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </Select>
    </div>
  )
}

/** "Page 2 of 5 · 43 results" with previous / next buttons; hidden while there is nothing to page. */
export function Pager<T>({
  data,
  list,
  noun = "result",
}: {
  data: Page<T> | null
  list: ListQueryState
  noun?: string
}) {
  if (!data) return null
  const count = `${data.totalItems} ${noun}${data.totalItems === 1 ? "" : "s"}`
  return (
    <nav className="pager" aria-label="Pages">
      <Button
        variant="secondary"
        disabled={data.page <= 0}
        onClick={() => list.setPage(data.page - 1)}
        aria-label="Previous page"
      >
        <ChevronLeft />
      </Button>
      <span aria-live="polite">
        {data.totalPages > 1 ? `Page ${data.page + 1} of ${data.totalPages} · ` : ""}
        {count}
      </span>
      <Button
        variant="secondary"
        disabled={data.page >= data.totalPages - 1}
        onClick={() => list.setPage(data.page + 1)}
        aria-label="Next page"
      >
        <ChevronRight />
      </Button>
    </nav>
  )
}
