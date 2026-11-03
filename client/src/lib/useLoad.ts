import { useEffect, useState } from "react"
import { api } from "./api"

export const errorText = (reason: unknown) =>
  reason instanceof Error
    ? reason.message
    : "Something went wrong. Please try again."

/**
 * Loads `path` from the API (with the staff `token` if given); `null` waits.
 * `reload()` fetches it again. While a new answer is on its way, `loading` is
 * true and `data` keeps the previous answer; answers for an older path are ignored.
 */
export function useLoad<T>(path: string | null, token?: string) {
  const [version, setVersion] = useState(0)
  const [data, setData] = useState<T | null>(null)
  const [done, setDone] = useState<{ key: string; error: string } | null>(null)
  const key = path === null ? null : `${version}:${path}`
  useEffect(() => {
    if (key === null || path === null) return
    let current = true
    api<T>(path, { token })
      .then((result) => {
        if (!current) return
        setData(result)
        setDone({ key, error: "" })
      })
      .catch((reason) => current && setDone({ key, error: errorText(reason) }))
    return () => {
      current = false
    }
  }, [key, path, token])
  return {
    data,
    loading: key !== null && done?.key !== key,
    error: done !== null && done.key === key ? done.error : "",
    reload: () => setVersion((v) => v + 1),
  }
}
