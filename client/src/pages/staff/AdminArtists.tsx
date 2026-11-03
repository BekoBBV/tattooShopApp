import { useEffect, useState, type FormEvent } from "react"
import { Plus } from "lucide-react"
import {
  Button,
  Eyebrow,
  Heading,
  Input,
  Select,
  Textarea,
} from "../../components/ui"
import { ListToolbar, Pager } from "../../components/ListControls"
import { api } from "../../lib/api"
import { useListQuery, type Page } from "../../lib/paging"
import type { Accent, AdminArtist, StaffBooking } from "../../lib/types"
import {
  accents,
  allDays,
  dayNames,
  plural,
  send,
  token,
  useAction,
  useStaffLoad,
} from "../../features/staff/staffUtils"
import {
  ConflictWarning,
  Feedback,
  Loading,
  ScheduleEditor,
} from "../../features/staff/StaffUi"
import { limits } from "../../lib/limits"

/** Admin: artists, their hours and days off, deactivation and logins (/admin/artists). */
type ArtistDraft = Omit<
  AdminArtist,
  "id" | "sortOrder" | "active" | "username" | "conflicts"
>
const emptyArtist: ArtistDraft = {
  name: "",
  handle: "",
  specialty: "",
  bio: "",
  photoUrl: null,
  accent: "red",
  weeklyHours: [],
  exceptions: [],
}
const toDraft = (item: AdminArtist): ArtistDraft => ({
  name: item.name,
  handle: item.handle,
  specialty: item.specialty,
  bio: item.bio,
  photoUrl: item.photoUrl,
  accent: item.accent,
  weeklyHours: item.weeklyHours,
  exceptions: item.exceptions,
})

export function AdminArtists() {
  const list = useListQuery({ sort: "order,asc", filters: { active: "", day: "" } })
  const load = useStaffLoad<Page<AdminArtist>>(`/admin/artists?${list.query}`)
  const { error, reload } = load
  const items = load.data?.items
  const [draft, setDraft] = useState(emptyArtist)
  const [editing, setEditing] = useState("")
  const [loginFor, setLoginFor] = useState<AdminArtist | null>(null)
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const action = useAction()
  // Bookings outside the hours of the artist just saved (shown under the form until the next edit)
  const [saved, setSaved] = useState<{ name: string; conflicts: StaffBooking[] } | null>(null)
  const login = useAction()
  const fieldError = action.fieldErrors
  // Bring the login form into view when it opens below the artist form
  useEffect(() => {
    if (loginFor)
      document
        .getElementById("artist-login")
        ?.scrollIntoView({ behavior: "smooth", block: "center" })
  }, [loginFor])
  async function save(event: FormEvent) {
    event.preventDefault()
    setSaved(null)
    const ok = await action.run(
      "save",
      async () => {
        const result = await api<AdminArtist>(
          editing ? `/admin/artists/${editing}` : "/admin/artists",
          {
            method: editing ? "PUT" : "POST",
            token: token(),
            body: JSON.stringify(draft),
          },
        )
        setSaved({ name: result.name, conflicts: result.conflicts })
      },
      editing
        ? "Artist saved."
        : "Artist added. Create a login so they can see their bookings.",
    )
    if (ok) {
      setDraft(emptyArtist)
      setEditing("")
      reload()
    }
  }
  const setActive = (item: AdminArtist, active: boolean) => {
    if (
      !active &&
      !window.confirm(
        `Deactivate ${item.name}? They disappear from the booking page and can no longer sign in.`,
      )
    )
      return
    action
      .run(
        item.id,
        // Only the active flag: hours the artist saved since this list loaded are kept
        () => send(`/admin/artists/${item.id}`, "PATCH", { active }),
        active ? `${item.name} is back on the booking page.` : `${item.name} is deactivated.`,
      )
      .then((ok) => ok && reload())
  }
  const remove = (item: AdminArtist) => {
    if (
      !window.confirm(
        `Delete ${item.name} and their login for good? Only possible for artists without any bookings.`,
      )
    )
      return
    action
      .run(item.id, () => send(`/admin/artists/${item.id}`, "DELETE"), `${item.name} was deleted.`)
      .then((ok) => ok && reload())
  }
  async function saveLogin(event: FormEvent) {
    event.preventDefault()
    if (!loginFor) return
    const ok = await login.run(
      "login",
      () => send(`/admin/artists/${loginFor.id}/login`, "POST", { username, password }),
      `Login saved for ${loginFor.name}. Share the username and password with them.`,
    )
    if (ok) {
      setPassword("")
      reload()
    }
  }
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>The crew</Eyebrow>
          <Heading level={1}>Artists.</Heading>
        </div>
      </div>
      <form className="details-form admin-form" onSubmit={save}>
        <Heading level={3}>{editing ? "Edit artist" : "Add artist"}</Heading>
        <Input
          label="Name"
          required
          maxLength={limits["artist.name"]}
          value={draft.name}
          error={fieldError.name}
          onChange={(e) => setDraft({ ...draft, name: e.target.value })}
        />
        <Input
          label="Handle"
          maxLength={limits["artist.handle"]}
          placeholder="@handle"
          value={draft.handle}
          error={fieldError.handle}
          onChange={(e) => setDraft({ ...draft, handle: e.target.value })}
        />
        <Input
          label="Specialty"
          maxLength={limits["artist.specialty"]}
          value={draft.specialty}
          error={fieldError.specialty}
          onChange={(e) => setDraft({ ...draft, specialty: e.target.value })}
        />
        <Textarea
          label="Bio"
          maxLength={limits["artist.bio"]}
          value={draft.bio}
          onChange={(e) => setDraft({ ...draft, bio: e.target.value })}
        />
        <Input
          label="Photo URL"
          maxLength={limits["artist.photoUrl"]}
          placeholder="https://… or /artists/photo.jpg"
          value={draft.photoUrl || ""}
          error={fieldError.photoUrl}
          onChange={(e) => setDraft({ ...draft, photoUrl: e.target.value || null })}
        />
        <Select
          label="Accent"
          value={draft.accent}
          onChange={(e) => setDraft({ ...draft, accent: e.target.value as Accent })}
        >
          {accents.map((item) => (
            <option key={item}>{item}</option>
          ))}
        </Select>
        <Heading level={3}>Weekly hours</Heading>
        <ScheduleEditor
          weeklyHours={draft.weeklyHours}
          exceptions={draft.exceptions}
          onChange={(weeklyHours, exceptions) =>
            setDraft({ ...draft, weeklyHours, exceptions })
          }
        />
        <Feedback error={action.error} notice={action.notice} />
        <ConflictWarning
          conflicts={saved?.conflicts ?? []}
          artistName={saved?.name}
          hint="They are still booked. Cancel them under All bookings if the artist can't take them."
        />
        <div className="row-actions">
          <Button type="submit" loading={action.busy === "save"}>
            <Plus /> {editing ? "Save artist" : "Add artist"}
          </Button>
          {editing ? (
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setEditing("")
                setDraft(emptyArtist)
                action.clear()
                setSaved(null)
              }}
            >
              Cancel edit
            </Button>
          ) : null}
        </div>
      </form>
      {loginFor ? (
        <form
          id="artist-login"
          className="details-form admin-form"
          onSubmit={saveLogin}
        >
          <Heading level={3}>Login for {loginFor.name}</Heading>
          <p>
            {loginFor.username
              ? `Current username: ${loginFor.username}. Saving sets a new password.`
              : "This artist has no login yet."}
          </p>
          <Input
            label="Username"
            required
            autoComplete="off"
            pattern="[A-Za-z0-9._\-]{3,30}"
            title="3–30 letters, digits, dots, dashes or underscores"
            maxLength={limits["artistLogin.username"]}
            value={username}
            error={login.fieldErrors.username}
            onChange={(e) => setUsername(e.target.value)}
          />
          <Input
            label="New password"
            type="password"
            required
            minLength={limits["user.passwordMin"]}
            maxLength={limits["user.password"]}
            autoComplete="new-password"
            value={password}
            error={login.fieldErrors.password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Feedback error={login.error} notice={login.notice} />
          <div className="row-actions">
            <Button type="submit" loading={login.busy === "login"}>
              Save login
            </Button>
            <Button type="button" variant="ghost" onClick={() => setLoginFor(null)}>
              Close
            </Button>
          </div>
        </form>
      ) : null}
      <Heading level={2}>All artists</Heading>
      <ListToolbar
        list={list}
        searchLabel="Search artists"
        placeholder="Name, handle, specialty…"
        sorts={[
          ["order,asc", "Shop order"],
          ["name,asc", "Name A–Z"],
          ["name,desc", "Name Z–A"],
        ]}
      >
        <Select
          label="Status"
          value={list.filters.active}
          onChange={(e) => list.setFilter("active", e.target.value)}
        >
          <option value="">All</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </Select>
        <Select
          label="Works on"
          value={list.filters.day}
          onChange={(e) => list.setFilter("day", e.target.value)}
        >
          <option value="">Any day</option>
          {allDays.map((day) => (
            <option key={day} value={day}>
              {dayNames[day]}
            </option>
          ))}
        </Select>
      </ListToolbar>
      {!items ? (
        <Loading error={error} />
      ) : !items.length ? (
        <p className="account-empty">No artists match this search.</p>
      ) : (
        <div className="booking-table">
          {items.map((item) => (
            <article key={item.id}>
              <div>
                <Heading level={3}>
                  {item.name} <small>{item.handle}</small>
                </Heading>
                <p>
                  {[
                    item.specialty,
                    plural(item.weeklyHours.length, "working day"),
                    item.exceptions.length
                      ? plural(item.exceptions.length, "upcoming exception")
                      : "",
                    item.username ? `login: ${item.username}` : "no login yet",
                    item.conflicts.length
                      ? `⚠ ${plural(item.conflicts.length, "booking")} outside hours`
                      : "",
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </div>
              {item.active ? null : <span className="status">Inactive</span>}
              <div className="row-actions">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditing(item.id)
                    setDraft(toDraft(item))
                    action.clear()
                    setSaved(null)
                    window.scrollTo({ top: 0, behavior: "smooth" })
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => {
                    setLoginFor(item)
                    setUsername(item.username || "")
                    setPassword("")
                    login.clear()
                  }}
                >
                  {item.username ? "Reset login" : "Create login"}
                </Button>
                <Button
                  variant="ghost"
                  loading={action.busy === item.id}
                  onClick={() => setActive(item, !item.active)}
                >
                  {item.active ? "Deactivate" : "Reactivate"}
                </Button>
                <Button
                  variant="danger"
                  disabled={action.busy === item.id}
                  onClick={() => remove(item)}
                >
                  Delete
                </Button>
              </div>
            </article>
          ))}
        </div>
      )}
      <Pager data={load.data} list={list} noun="artist" />
    </div>
  )
}
