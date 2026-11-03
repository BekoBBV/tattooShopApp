import { useState, type FormEvent } from "react"
import { Plus } from "lucide-react"
import { ListToolbar, Pager } from "../../components/ListControls"
import { Button, Checkbox, Eyebrow, Heading, Input, Select } from "../../components/ui"
import { getSession } from "../../lib/api"
import { useListQuery, type Page } from "../../lib/paging"
import type { AdminUser, ArtistOption, Role } from "../../lib/types"
import {
  formatDateTime,
  plural,
  send,
  useAction,
  useStaffLoad,
} from "../../features/staff/staffUtils"
import { Feedback, Loading } from "../../features/staff/StaffUi"
import { limits } from "../../lib/limits"

const roleNames: Record<Role, string> = {
  USER: "Customer",
  ARTIST: "Artist",
  ADMIN: "Admin",
}

/** What each access level may do (shown on the page, so the rights are clear when changing them). */
const levels: [string, string][] = [
  ["Guest (no login)", "Sees the site and the plain lists, books and cancels with code + phone."],
  ["Customer", "Also searches, sorts and pages the lists, and has My bookings."],
  ["Artist", "Staff area: their own bookings (done / no-show / cancel) and working hours."],
  ["Admin", "Everything: all bookings, services, artists, reviews, messages, stats and these logins."],
]

type Draft = {
  role: Role
  username: string
  password: string
  active: boolean
  name: string
  email: string
  phone: string
  artistId: string
}
const emptyDraft: Draft = {
  role: "USER",
  username: "",
  password: "",
  active: true,
  name: "",
  email: "",
  phone: "",
  artistId: "",
}

/** Admin: every login and its rights (/admin/users). */
export function AdminUsers() {
  const me = getSession()?.user.username
  const list = useListQuery({ sort: "role,asc", filters: { role: "", active: "" } })
  const load = useStaffLoad<Page<AdminUser>>(`/admin/users?${list.query}`)
  const artists = useStaffLoad<ArtistOption[]>("/admin/artists/options")
  const [draft, setDraft] = useState(emptyDraft)
  const [editing, setEditing] = useState<AdminUser | null>(null)
  const action = useAction()
  const fieldError = action.fieldErrors
  const set = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }))
  const items = load.data?.items

  function startEdit(user: AdminUser) {
    setEditing(user)
    setDraft({
      role: user.role,
      username: user.username,
      password: "",
      active: user.active,
      name: user.name ?? "",
      email: user.email ?? "",
      phone: user.phone ?? "",
      artistId: user.artistId ?? "",
    })
    action.clear()
    window.scrollTo({ top: 0, behavior: "smooth" })
  }
  function stopEdit() {
    setEditing(null)
    setDraft(emptyDraft)
    action.clear()
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    const body = {
      role: draft.role,
      username: draft.role === "USER" ? null : draft.username,
      password: editing ? null : draft.password,
      active: draft.active,
      name: draft.name || null,
      email: draft.email || null,
      phone: draft.phone || null,
      artistId: draft.role === "ARTIST" ? draft.artistId : null,
    }
    const ok = await action.run(
      "save",
      async () => {
        if (!editing) return send("/admin/users", "POST", body)
        await send(`/admin/users/${editing.id}`, "PUT", body)
        if (draft.password) await send(`/admin/users/${editing.id}/password`, "PUT", { password: draft.password })
      },
      editing
        ? `Saved. ${editing.username} has to sign in again if their role, artist or password changed.`
        : "Login added.",
    )
    if (ok) {
      setEditing(null)
      setDraft(emptyDraft)
      load.reload()
    }
  }

  function setBlocked(user: AdminUser, blocked: boolean) {
    if (blocked && !window.confirm(`Block ${user.username}? They are signed out and can't sign in until unblocked.`))
      return
    action
      .run(
        user.id,
        () =>
          send(`/admin/users/${user.id}`, "PUT", {
            role: user.role,
            active: !blocked,
            name: user.name,
            phone: user.phone,
            artistId: user.artistId,
          }),
        blocked ? `${user.username} is blocked.` : `${user.username} can sign in again.`,
      )
      .then((ok) => ok && load.reload())
  }

  function remove(user: AdminUser) {
    if (!window.confirm(`Delete the login ${user.username}? This can't be undone.`)) return
    action
      .run(user.id, () => send(`/admin/users/${user.id}`, "DELETE"), `${user.username} was deleted.`)
      .then((ok) => ok && load.reload())
  }

  // Artists without a login, plus the one this login already belongs to
  const freeArtists = (artists.data || []).filter(
    (b) => !b.username || b.id === editing?.artistId,
  )

  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Access</Eyebrow>
          <Heading level={1}>Users & rights.</Heading>
        </div>
      </div>
      <dl className="access-levels">
        {levels.map(([level, rights]) => (
          <div key={level}>
            <dt>{level}</dt>
            <dd>{rights}</dd>
          </div>
        ))}
      </dl>

      <form className="details-form admin-form" onSubmit={save}>
        <Heading level={3}>{editing ? `Edit ${editing.username}` : "Add a login"}</Heading>
        <Select
          label="Role"
          value={draft.role}
          disabled={editing?.username === me}
          onChange={(e) => set({ role: e.target.value as Role })}
        >
          <option value="USER">Customer</option>
          <option value="ARTIST">Artist</option>
          <option value="ADMIN">Admin</option>
        </Select>
        {draft.role === "USER" ? (
          <>
            <Input
              label="Name"
              required
              maxLength={limits["user.name"]}
              value={draft.name}
              error={fieldError.name}
              onChange={(e) => set({ name: e.target.value })}
            />
            <Input
              label={editing ? "Email (their sign-in, can't be changed)" : "Email (their sign-in)"}
              type="email"
              required
              maxLength={limits["user.email"]}
              disabled={!!editing}
              value={draft.email}
              error={fieldError.email}
              onChange={(e) => set({ email: e.target.value })}
            />
            <Input
              label="Phone"
              type="tel"
              required
              maxLength={limits["user.phone"]}
              value={draft.phone}
              error={fieldError.phone}
              onChange={(e) => set({ phone: e.target.value })}
            />
          </>
        ) : (
          <>
            {draft.role === "ARTIST" ? (
              <Select
                label="Artist"
                required
                value={draft.artistId}
                onChange={(e) => set({ artistId: e.target.value })}
              >
                <option value="">Pick an artist…</option>
                {freeArtists.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </Select>
            ) : null}
            <Input
              label={editing ? "Username (can't be changed)" : "Username"}
              required
              pattern="[A-Za-z0-9._\-]{3,30}"
              title="3-30 letters, digits, dots, dashes or underscores"
              maxLength={limits["user.username"]}
              disabled={!!editing}
              value={draft.username}
              error={fieldError.username}
              onChange={(e) => set({ username: e.target.value })}
            />
          </>
        )}
        <Input
          label={editing ? "New password (leave empty to keep it)" : "Password (8+ characters)"}
          type="password"
          autoComplete="new-password"
          required={!editing}
          minLength={limits["user.passwordMin"]}
          maxLength={limits["user.password"]}
          value={draft.password}
          error={fieldError.password}
          onChange={(e) => set({ password: e.target.value })}
        />
        {editing ? (
          <Checkbox
            className="policy-check"
            label="Can sign in (untick to block)"
            checked={draft.active}
            disabled={editing.username === me}
            onChange={(e) => set({ active: e.target.checked })}
          />
        ) : null}
        <Feedback error={action.error} notice={action.notice} />
        <div className="row-actions">
          <Button type="submit" loading={action.busy === "save"}>
            <Plus /> {editing ? "Save changes" : "Add login"}
          </Button>
          {editing ? (
            <Button type="button" variant="ghost" onClick={stopEdit}>
              Cancel edit
            </Button>
          ) : null}
        </div>
      </form>

      <Heading level={2}>All logins</Heading>
      <ListToolbar
        list={list}
        searchLabel="Search logins"
        placeholder="Username, name, email, phone…"
        sorts={[
          ["role,asc", "Role"],
          ["username,asc", "Username A–Z"],
          ["name,asc", "Name A–Z"],
          ["created,desc", "Newest first"],
          ["lastLogin,desc", "Last signed in"],
        ]}
      >
        <Select
          label="Role"
          value={list.filters.role}
          onChange={(e) => list.setFilter("role", e.target.value)}
        >
          <option value="">All</option>
          <option value="USER">Customers</option>
          <option value="ARTIST">Artists</option>
          <option value="ADMIN">Admins</option>
        </Select>
        <Select
          label="Status"
          value={list.filters.active}
          onChange={(e) => list.setFilter("active", e.target.value)}
        >
          <option value="">All</option>
          <option value="true">Can sign in</option>
          <option value="false">Blocked</option>
        </Select>
      </ListToolbar>
      {!items ? (
        <Loading error={load.error} />
      ) : !items.length ? (
        <p className="account-empty">No logins match this search.</p>
      ) : (
        <div className="booking-table">
          {items.map((user) => {
            const self = user.username === me
            return (
              <article key={user.id}>
                <div>
                  <Heading level={3}>
                    {user.name || user.username} {self ? <small>(you)</small> : null}
                  </Heading>
                  <p>
                    {[
                      user.name ? user.username : "",
                      user.role === "ARTIST" ? user.artistName : "",
                      user.phone,
                      user.lastLoginAt
                        ? `signed in ${plural(user.loginCount, "time")}, last ${formatDateTime(user.lastLoginAt)}`
                        : "never signed in",
                    ]
                      .filter(Boolean)
                      .join(" · ")}
                  </p>
                </div>
                <span className={`status role-${user.role.toLowerCase()}`}>
                  {roleNames[user.role]}
                  {user.active ? "" : " · blocked"}
                </span>
                <div className="row-actions">
                  <Button variant="secondary" onClick={() => startEdit(user)}>
                    Edit
                  </Button>
                  {self ? null : (
                    <>
                      <Button
                        variant="ghost"
                        loading={action.busy === user.id}
                        onClick={() => setBlocked(user, user.active)}
                      >
                        {user.active ? "Block" : "Unblock"}
                      </Button>
                      <Button
                        variant="danger"
                        disabled={action.busy === user.id}
                        onClick={() => remove(user)}
                      >
                        Delete
                      </Button>
                    </>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}
      <Pager data={load.data} list={list} noun="login" />
    </div>
  )
}
