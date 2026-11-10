import { Plus } from "lucide-react"
import { useState } from "react"
import { FlashDialog } from "../../components/FlashDialog"
import { Pager, StaffHeader, StaffState, StaffSwitch, StatusLine } from "../../components/StaffUi"
import { euros, size } from "../../lib/format"
import { json, staffApi, useStaff } from "../../lib/staff"
import { flashStateLabel } from "../../lib/staffText"
import type { AdminFlash as Flash, ArtistOption, Page } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

/** Flash catalogue: every artist's designs, on or off the sheet. */
export default function AdminFlash() {
  usePageTitle("Flash catalogue")
  const staff = useStaff()
  const token = staff?.token
  const [artistId, setArtistId] = useState("")
  const [available, setAvailable] = useState("")
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Flash | "new" | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")
  const flash = useLoad<Page<Flash>>(
    `/admin/flash?page=${page}&size=48&sort=order,asc${artistId ? `&artistId=${artistId}` : ""}${available ? `&available=${available}` : ""}`,
    token,
  )
  const artists = useLoad<ArtistOption[]>("/admin/artists/options", token)

  const say = (text: string) => {
    setNote(text)
    setProblem("")
  }

  async function toggle(f: Flash, on: boolean) {
    try {
      await staffApi(`/admin/flash/${f.id}`, json("PATCH", { active: on }))
      say(on ? `${f.name} is back on the flash sheet.` : `${f.name} is off the flash sheet.`)
      flash.reload()
    } catch (reason) {
      setProblem(errorText(reason))
    }
  }

  const items = flash.data?.items ?? []
  return (
    <>
      <StaffHeader
        title="Flash catalogue"
        action={
          <button className="button button-primary staff-compact" type="button" disabled={!artists.data} onClick={() => setEditing("new")}>
            <Plus className="icon" size={18} aria-hidden="true" /> Add flash design
          </button>
        }
      >
        All artists' bookable designs.
      </StaffHeader>
      <StatusLine>{note}</StatusLine>
      <StatusLine error>{problem}</StatusLine>
      <div className="admin-filters">
        <label>
          Artist
          <select value={artistId} onChange={(e) => { setArtistId(e.target.value); setPage(0) }}>
            <option value="">All</option>
            {artists.data?.map((a) => (
              <option key={a.id} value={a.id}>{a.name}</option>
            ))}
          </select>
        </label>
        <label>
          On the sheet
          <select value={available} onChange={(e) => { setAvailable(e.target.value); setPage(0) }}>
            <option value="">All</option>
            <option value="true">Available</option>
            <option value="false">Taken off</option>
          </select>
        </label>
      </div>
      <StaffState loading={flash.loading && !flash.data} error={flash.error} onRetry={flash.reload}
        empty={flash.data && items.length === 0 && "No designs match."} />
      <div className="admin-flash-grid">
        {items.map((f) => (
          <article key={f.id}>
            <div className="staff-image">{f.image && <img src={f.image.thumbUrl} alt="" />}</div>
            <div>
              <h3>{f.name}</h3>
              <p>
                {f.artistName}, {size(f.widthCm, f.heightCm)}, {euros(f.price)}
              </p>
              {f.state !== "ON_SHEET" && f.state !== "HIDDEN" && (
                <p className="flash-state">
                  {flashStateLabel[f.state]}
                  {f.bookingCode ? `, ${f.bookingCode}` : ""}
                </p>
              )}
            </div>
            <div className="card-actions">
              <StaffSwitch label="Available" hiddenLabel={`${f.name} on the flash sheet`} checked={f.available} onChange={(on) => toggle(f, on)} />
              <button className="staff-text-button" type="button" aria-label={`Edit ${f.name}`} onClick={() => setEditing(f)}>
                Edit
              </button>
            </div>
          </article>
        ))}
      </div>
      {flash.data && <Pager page={flash.data.page} totalPages={flash.data.totalPages} onPage={setPage} />}
      {editing && artists.data && (
        <FlashDialog
          flash={editing === "new" ? null : editing}
          artists={artists.data}
          onClose={() => setEditing(null)}
          onSaved={(f, created) => {
            setEditing(null)
            say(created ? `${f.name} added for ${f.artistName}.` : `${f.name} saved.`)
            flash.reload()
          }}
          onDeleted={(f) => {
            setEditing(null)
            say(`${f.name} deleted.`)
            flash.reload()
          }}
        />
      )}
    </>
  )
}
