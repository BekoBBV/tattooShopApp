import { ImagePlus, Plus } from "lucide-react"
import { useMemo, useState, type FormEvent } from "react"
import { ConfirmDialog, StaffHeader, StaffState, StaffSwitch, StatusLine } from "../../components/StaffUi"
import { ArtistSeal, Dialog, FormError, SelectField, TextAreaField, TextField } from "../../components/Studio"
import { ApiError } from "../../lib/api"
import { euros } from "../../lib/format"
import { limits } from "../../lib/limits"
import { json, multipart, PICTURE_TYPES, pictureProblem, staffApi, usePreviewUrls, useStaff } from "../../lib/staff"
import { hoursSummary } from "../../lib/staffText"
import type { AdminArtist, Page } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

/** A new artist starts with the studio's opening hours; they change them under My hours. */
const STUDIO_HOURS = (["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const).map((day) => ({ day, start: "11:00", end: "23:00" }))

/** Artists: profiles, rates, staff logins and who is active. */
export default function AdminArtists() {
  usePageTitle("Artists")
  const staff = useStaff()
  const artists = useLoad<Page<AdminArtist>>("/admin/artists?size=100&sort=order,asc", staff?.token)
  const [editing, setEditing] = useState<AdminArtist | "new" | null>(null)
  const [login, setLogin] = useState<AdminArtist | null>(null)
  const [deleting, setDeleting] = useState<AdminArtist | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")

  const say = (text: string) => {
    setNote(text)
    setProblem("")
    artists.reload()
  }

  async function setActive(a: AdminArtist, active: boolean) {
    try {
      await staffApi(`/admin/artists/${a.id}`, json("PATCH", { active }))
      say(active ? `${a.name} is active again: their page and designs are back.` : `${a.name} is inactive: their page, designs and login are off.`)
    } catch (reason) {
      setNote("")
      setProblem(errorText(reason))
    }
  }

  const items = artists.data?.items ?? []
  return (
    <>
      <StaffHeader
        title="Artists"
        action={
          <button className="button button-primary staff-compact" type="button" onClick={() => setEditing("new")}>
            <Plus className="icon" size={18} aria-hidden="true" /> Add artist
          </button>
        }
      >
        Profiles, rates and staff access.
      </StaffHeader>
      <StatusLine>{note}</StatusLine>
      <StatusLine error>{problem}</StatusLine>
      <StaffState loading={artists.loading && !artists.data} error={artists.error} onRetry={artists.reload}
        empty={artists.data && items.length === 0 && "No artists yet."} />
      <div className="admin-artist-list">
        {items.map((a) => (
          <article key={a.id} className={a.active ? "" : "is-inactive"}>
            <ArtistSeal name={a.name} px={48} />
            <div>
              <h2>{a.name}</h2>
              <p>{a.style || "No style set"}</p>
              <div className="table-actions artist-actions">
                <button className="staff-text-button" type="button" onClick={() => setEditing(a)}>Edit</button>
                <button className="staff-text-button" type="button" onClick={() => setLogin(a)}>
                  {a.username ? "Reset login" : "Create login"}
                </button>
                <button className="staff-text-button" type="button" onClick={() => setDeleting(a)}>Delete</button>
              </div>
            </div>
            <dl>
              <div>
                <dt>Rates</dt>
                <dd>{a.hourlyRate || a.dayRate ? [a.hourlyRate && `${euros(a.hourlyRate)} per hour`, a.dayRate && `${euros(a.dayRate)} full day`].filter(Boolean).join(", ") : "Not set"}</dd>
              </div>
              <div>
                <dt>Staff login</dt>
                <dd>{a.username ?? "None yet"}</dd>
              </div>
              <div>
                <dt>Hours</dt>
                <dd>{hoursSummary(a.weeklyHours)}</dd>
              </div>
              {a.conflicts.length > 0 && (
                <div>
                  <dt>Note</dt>
                  <dd className="artist-warning">
                    {a.conflicts.length === 1 ? "1 booking falls" : `${a.conflicts.length} bookings fall`} outside their hours
                  </dd>
                </div>
              )}
            </dl>
            <StaffSwitch label="Active" hiddenLabel={`${a.name} active`} checked={a.active} onChange={(on) => setActive(a, on)} />
          </article>
        ))}
      </div>

      {editing && (
        <ArtistDialog
          artist={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(a, created) => {
            setEditing(null)
            say(created ? `${a.name} added. Create their staff login so they can sign in.` : `${a.name} saved.`)
          }}
        />
      )}
      {login && (
        <LoginDialog
          artist={login}
          onClose={() => setLogin(null)}
          onSaved={(username) => {
            setLogin(null)
            say(`Login ${username} saved for ${login.name}. Any old sign-ins of theirs have ended.`)
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title={`Delete ${deleting.name}?`}
          confirmLabel="Delete artist"
          onClose={() => setDeleting(null)}
          onConfirm={async () => {
            await staffApi(`/admin/artists/${deleting.id}`, { method: "DELETE" })
            const name = deleting.name
            setDeleting(null)
            say(`${name} deleted with their login, designs and portfolio.`)
          }}
        >
          Their login, flash designs and portfolio go too. This only works for artists who never had a booking; for anyone else,
          switch them off instead.
        </ConfirmDialog>
      )}
    </>
  )
}

function ArtistDialog({ artist, onClose, onSaved }: { artist: AdminArtist | null; onClose: () => void; onSaved: (a: AdminArtist, created: boolean) => void }) {
  const [values, setValues] = useState({
    name: artist?.name ?? "",
    slug: artist?.slug ?? "",
    style: artist?.style ?? "",
    bio: artist?.bio ?? "",
    hourlyRate: artist?.hourlyRate != null ? String(artist.hourlyRate) : "",
    dayRate: artist?.dayRate != null ? String(artist.dayRate) : "",
    breakMinutes: String(artist?.breakMinutes ?? 30),
  })
  const [picture, setPicture] = useState<File | null>(null)
  const [removePicture, setRemovePicture] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })

  const chosen = useMemo(() => [picture], [picture])
  const [preview] = usePreviewUrls(chosen)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!values.name.trim()) found.name = "Enter the artist's name."
    if (values.slug && !/^[a-z0-9-]+$/.test(values.slug)) found.slug = "Use lower-case letters, digits and dashes."
    for (const field of ["hourlyRate", "dayRate"] as const) {
      if (values[field] !== "" && !(Number(values[field]) >= 0)) found[field] = "Enter an amount in euros, or leave it empty."
    }
    setErrors(found)
    if (Object.keys(found).length) return
    const body = {
      name: values.name.trim(),
      slug: values.slug.trim() || null,
      style: values.style.trim(),
      bio: values.bio.trim(),
      hourlyRate: values.hourlyRate === "" ? null : Number(values.hourlyRate),
      dayRate: values.dayRate === "" ? null : Number(values.dayRate),
      breakMinutes: Number(values.breakMinutes),
      weeklyHours: artist?.weeklyHours ?? STUDIO_HOURS,
      exceptions: artist?.exceptions ?? [],
    }
    setBusy(true)
    setProblem("")
    try {
      let saved = artist
        ? await staffApi<AdminArtist>(`/admin/artists/${artist.id}`, json("PUT", body))
        : await staffApi<AdminArtist>("/admin/artists", json("POST", body))
      if (picture) {
        const image = await staffApi<AdminArtist["signaturePiece"]>(`/admin/artists/${saved.id}/signature`, multipart("PUT", { image: picture }))
        saved = { ...saved, signaturePiece: image }
      } else if (removePicture && artist?.signaturePiece) {
        await staffApi(`/admin/artists/${saved.id}/signature`, { method: "DELETE" })
      }
      onSaved(saved, !artist)
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setErrors(reason.errors)
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  const current = removePicture ? null : (preview ?? artist?.signaturePiece?.thumbUrl ?? null)
  return (
    <Dialog title={artist ? `Edit ${artist.name}` : "Add artist"} titleId="artist-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      <TextField label="Name" name="name" maxLength={limits["artist.name"]} value={values.name} onChange={set("name")} error={errors.name} />
      <TextField label="Page address" name="slug" maxLength={limits["artist.slug"]} value={values.slug} onChange={set("slug")} error={errors.slug}
        helper={`/artists/${values.slug || "made-from-the-name"}`} />
      <TextField label="Style" name="style" placeholder="Bold traditional colour" maxLength={limits["artist.style"]} value={values.style} onChange={set("style")} />
      <TextAreaField label="About" name="bio" rows={4} maxLength={limits["artist.bio"]} value={values.bio} onChange={set("bio")} />
      <div className="panel-row dialog-row">
        <TextField label="Per hour €" name="hourlyRate" type="number" inputMode="decimal" min={0} value={values.hourlyRate} onChange={set("hourlyRate")} error={errors.hourlyRate} />
        <TextField label="Full day €" name="dayRate" type="number" inputMode="decimal" min={0} value={values.dayRate} onChange={set("dayRate")} error={errors.dayRate} />
      </div>
      <SelectField label="Break after each client" name="breakMinutes" value={values.breakMinutes} onChange={set("breakMinutes")}
        options={[0, 30, 60, 90, 120].map((m) => ({ value: String(m), label: `${m} minutes` }))} />
      <div className="dialog-picture">
        <div className="staff-image">{current && <img src={current} alt="" />}</div>
        <div>
          <label className="button button-secondary file-button staff-compact">
            <ImagePlus className="icon" size={18} aria-hidden="true" /> {current ? "Replace signature piece" : "Add signature piece"}
            <input type="file" accept={PICTURE_TYPES} onChange={(e) => {
              const file = e.target.files?.[0] ?? null
              e.target.value = ""
              if (!file) return
              const why = pictureProblem(file)
              setErrors({ ...errors, picture: why ?? "" })
              if (!why) {
                setPicture(file)
                setRemovePicture(false)
              }
            }} />
          </label>
          {current && (
            <button className="staff-text-button" type="button" onClick={() => { setPicture(null); setRemovePicture(true) }}>
              Remove
            </button>
          )}
          {errors.picture ? <p className="field-error">{errors.picture}</p> : <p className="helper">Shown on their hanging scroll.</p>}
        </div>
      </div>
      {!artist && <p className="dialog-hint">They start with the studio's hours, Tuesday to Sunday 11:00 to 23:00, and change them under My hours.</p>}
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Saving..." : artist ? "Save artist" : "Add artist"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>Cancel</button>
      </div>
    </Dialog>
  )
}

/** The artist's one staff login: create it, or reset the username and password. */
function LoginDialog({ artist, onClose, onSaved }: { artist: AdminArtist; onClose: () => void; onSaved: (username: string) => void }) {
  const [username, setUsername] = useState(artist.username ?? artist.slug)
  const [password, setPassword] = useState("")
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!/^[A-Za-z0-9._-]{3,30}$/.test(username.trim())) found.username = "3 to 30 letters, digits, dots, dashes or underscores."
    if (password.length < limits["user.passwordMin"]) found.password = `At least ${limits["user.passwordMin"]} characters.`
    setErrors(found)
    if (Object.keys(found).length) return
    setBusy(true)
    setProblem("")
    try {
      const saved = await staffApi<{ username: string }>(`/admin/artists/${artist.id}/login`, json("POST", { username: username.trim(), password }))
      onSaved(saved.username)
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setErrors(reason.errors)
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  return (
    <Dialog title={`${artist.username ? "Reset" : "Create"} ${artist.name}'s login`} titleId="login-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      <TextField label="Username" name="username" autoComplete="off" autoCapitalize="none" spellCheck={false} maxLength={limits["artistLogin.username"]}
        value={username} onChange={(e) => setUsername(e.target.value)} error={errors.username} />
      <TextField label="New password" name="password" type="password" autoComplete="new-password" maxLength={limits["user.password"]}
        value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password}
        helper="At least 8 characters. Give it to them in person; they are signed out everywhere else." />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>{busy ? "Saving..." : "Save login"}</button>
        <button className="button button-secondary" type="button" onClick={onClose}>Cancel</button>
      </div>
    </Dialog>
  )
}
