import { Plus } from "lucide-react"
import { useEffect, useState, type FormEvent } from "react"
import { ConfirmDialog, Pager, StaffHeader, StaffState, StaffSwitch, StatusLine } from "../../components/StaffUi"
import { Dialog, FormError, SelectField, TextField } from "../../components/Studio"
import { ApiError } from "../../lib/api"
import { limits } from "../../lib/limits"
import { json, staffApi, useStaff } from "../../lib/staff"
import { whenAt } from "../../lib/staffText"
import type { AdminUser, ArtistOption, Page, Role } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

const ROLE_LABEL: Record<Role, string> = { USER: "Customer", ARTIST: "Artist", ADMIN: "Admin" }

/** The body PUT /admin/users/{id} needs: everything as it is, with `change` on top. */
const asRequest = (u: AdminUser, change: Partial<{ role: Role; active: boolean; artistId: string | null; name: string; phone: string }>) => ({
  role: u.role,
  active: u.active,
  artistId: u.artistId,
  name: u.name,
  phone: u.phone,
  ...change,
})

/** Users: customer accounts and staff logins, who may sign in, and their passwords. */
export default function AdminUsers() {
  usePageTitle("Users")
  const staff = useStaff()
  const token = staff?.token
  const [role, setRole] = useState("")
  const [typed, setTyped] = useState("")
  const [q, setQ] = useState("")
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<AdminUser | "new" | null>(null)
  const [resetting, setResetting] = useState<AdminUser | null>(null)
  const [deleting, setDeleting] = useState<AdminUser | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")
  useEffect(() => {
    const timer = window.setTimeout(() => setQ(typed.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [typed])
  const users = useLoad<Page<AdminUser>>(
    `/admin/users?page=${page}&size=25&sort=role,asc${role ? `&role=${role}` : ""}${q ? `&q=${encodeURIComponent(q)}` : ""}`,
    token,
  )
  const artists = useLoad<ArtistOption[]>("/admin/artists/options", token)

  const say = (text: string) => {
    setNote(text)
    setProblem("")
    users.reload()
  }
  const nameOf = (u: AdminUser) => u.name ?? u.artistName ?? u.username
  const isMe = (u: AdminUser) => u.username === staff?.user.username

  async function setActive(u: AdminUser, active: boolean) {
    try {
      await staffApi(`/admin/users/${u.id}`, json("PUT", asRequest(u, { active })))
      say(active ? `${nameOf(u)} can sign in again.` : `${nameOf(u)} is blocked and signed out everywhere.`)
    } catch (reason) {
      setNote("")
      setProblem(errorText(reason))
    }
  }

  const items = users.data?.items ?? []
  return (
    <>
      <StaffHeader
        title="Users"
        action={
          <button className="button button-primary staff-compact" type="button" onClick={() => setEditing("new")}>
            <Plus className="icon" size={18} aria-hidden="true" /> Add user
          </button>
        }
      >
        Customer and staff access.
      </StaffHeader>
      <StatusLine>{note}</StatusLine>
      <StatusLine error>{problem}</StatusLine>
      <div className="admin-filters">
        <label>
          Role
          <select value={role} onChange={(e) => { setRole(e.target.value); setPage(0) }}>
            <option value="">All</option>
            <option value="USER">Customers</option>
            <option value="ARTIST">Artists</option>
            <option value="ADMIN">Admins</option>
          </select>
        </label>
        <label>
          Search
          <input type="search" value={typed} maxLength={limits["search.q"]} placeholder="Name, email, phone"
            onChange={(e) => { setTyped(e.target.value); setPage(0) }} />
        </label>
      </div>
      <StaffState loading={users.loading && !users.data} error={users.error} onRetry={users.reload}
        empty={users.data && items.length === 0 && "No users match."} />
      {items.length > 0 && (
        <div className="table-scroll">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email or username</th>
                <th>Role</th>
                <th>Last sign-in</th>
                <th>Active</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((u) => (
                <tr key={u.id}>
                  <td>
                    {nameOf(u)}
                    {isMe(u) && <small>You</small>}
                  </td>
                  <td>{u.email ?? u.username}</td>
                  <td>
                    {ROLE_LABEL[u.role]}
                    {u.role === "ARTIST" && u.artistName && u.artistName !== nameOf(u) ? `, ${u.artistName}` : ""}
                  </td>
                  <td>{u.lastLoginAt ? whenAt(u.lastLoginAt) : "Never"}</td>
                  <td>
                    <StaffSwitch label={u.active ? "Active" : "Blocked"} hiddenLabel={`${nameOf(u)} may sign in`} checked={u.active}
                      disabled={isMe(u)} onChange={(on) => setActive(u, on)} />
                  </td>
                  <td>
                    <div className="table-actions">
                      <button className="staff-text-button" type="button" onClick={() => setEditing(u)}>Edit</button>
                      <button className="staff-text-button" type="button" onClick={() => setResetting(u)}>Reset password</button>
                      {!isMe(u) && (
                        <button className="staff-text-button" type="button" onClick={() => setDeleting(u)}>Delete</button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {users.data && <Pager page={users.data.page} totalPages={users.data.totalPages} onPage={setPage} />}

      {editing && artists.data && (
        <UserDialog
          user={editing === "new" ? null : editing}
          me={editing !== "new" && isMe(editing)}
          artists={artists.data}
          onClose={() => setEditing(null)}
          onSaved={(u, created) => {
            setEditing(null)
            say(created ? `${ROLE_LABEL[u.role]} ${nameOf(u)} added.` : `${nameOf(u)} saved.`)
          }}
        />
      )}
      {resetting && (
        <PasswordDialog
          user={resetting}
          onClose={() => setResetting(null)}
          onSaved={() => {
            const who = nameOf(resetting)
            setResetting(null)
            say(`New password set for ${who}. They are signed out everywhere and sign in with the new one.`)
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title={`Delete ${nameOf(deleting)}?`}
          confirmLabel="Delete user"
          onClose={() => setDeleting(null)}
          onConfirm={async () => {
            await staffApi(`/admin/users/${deleting.id}`, { method: "DELETE" })
            const who = nameOf(deleting)
            setDeleting(null)
            say(`${who} deleted.`)
          }}
        >
          {deleting.role === "USER"
            ? "Their account goes; their bookings stay with the studio and still work with their code."
            : "Their login goes. To keep it but stop them signing in, switch it off instead."}
        </ConfirmDialog>
      )}
    </>
  )
}

/** Add a user, or change one's role, artist and (customers) name and phone. */
function UserDialog({ user, me, artists, onClose, onSaved }: {
  user: AdminUser | null
  me: boolean
  artists: ArtistOption[]
  onClose: () => void
  onSaved: (u: AdminUser, created: boolean) => void
}) {
  const [values, setValues] = useState({
    role: (user?.role ?? "ADMIN") as Role,
    username: user?.username ?? "",
    artistId: user?.artistId ?? artists.find((a) => !a.username)?.id ?? artists[0]?.id ?? "",
    name: user?.name ?? "",
    email: user?.email ?? "",
    phone: user?.phone ?? "",
    password: "",
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (field: keyof typeof values) => (e: { target: { value: string } }) => setValues({ ...values, [field]: e.target.value })
  const customer = values.role === "USER"

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    if (!user && !customer && !/^[A-Za-z0-9._-]{3,30}$/.test(values.username.trim())) found.username = "3 to 30 letters, digits, dots, dashes or underscores."
    if (customer && !values.name.trim()) found.name = "Enter their name."
    if (customer && !values.phone.trim()) found.phone = "Enter their phone number."
    if (!user && customer && !values.email.trim()) found.email = "Enter their email: they sign in with it."
    if (values.role === "ARTIST" && !values.artistId) found.artistId = "Choose the artist."
    if (!user && values.password.length < limits["user.passwordMin"]) found.password = `At least ${limits["user.passwordMin"]} characters.`
    setErrors(found)
    if (Object.keys(found).length) return
    setBusy(true)
    setProblem("")
    try {
      const body = {
        role: values.role,
        artistId: values.role === "ARTIST" ? values.artistId : null,
        name: customer ? values.name.trim() : null,
        phone: customer ? values.phone.trim() : null,
      }
      const saved = user
        ? await staffApi<AdminUser>(`/admin/users/${user.id}`, json("PUT", { ...body, active: user.active }))
        : await staffApi<AdminUser>(
            "/admin/users",
            json("POST", { ...body, username: customer ? null : values.username.trim(), email: customer ? values.email.trim() : null, password: values.password }),
          )
      onSaved(saved, !user)
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setErrors(reason.errors)
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  return (
    <Dialog title={user ? `Edit ${user.name ?? user.username}` : "Add user"} titleId="user-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      {me ? (
        <p className="dialog-hint">This is your own login: another admin has to change its role.</p>
      ) : (
        <SelectField label="Role" name="role" value={values.role} onChange={set("role")}
          options={(user?.role === "USER" || !user ? (["USER", "ARTIST", "ADMIN"] as Role[]) : (["ARTIST", "ADMIN"] as Role[])).map((r) => ({ value: r, label: ROLE_LABEL[r] }))} />
      )}
      {user && <p className="dialog-hint">Signs in as {user.username}. A new role or artist signs them out.</p>}
      {!user && !customer && (
        <TextField label="Username" name="username" autoComplete="off" autoCapitalize="none" spellCheck={false} maxLength={limits["user.username"]}
          value={values.username} onChange={set("username")} error={errors.username} />
      )}
      {values.role === "ARTIST" && (
        <SelectField label="Artist" name="artistId" value={values.artistId} onChange={set("artistId")} error={errors.artistId}
          options={artists.map((a) => ({ value: a.id, label: `${a.name}${a.username && a.id !== user?.artistId ? ` (has login ${a.username})` : ""}` }))} />
      )}
      {customer && (
        <>
          <TextField label="Name" name="name" maxLength={limits["user.name"]} value={values.name} onChange={set("name")} error={errors.name} />
          {!user && (
            <TextField label="Email" name="email" type="email" maxLength={limits["user.email"]} value={values.email} onChange={set("email")} error={errors.email} />
          )}
          <TextField label="Phone" name="phone" type="tel" maxLength={limits["user.phone"]} value={values.phone} onChange={set("phone")} error={errors.phone} />
        </>
      )}
      {!user && (
        <TextField label="Password" name="password" type="password" autoComplete="new-password" maxLength={limits["user.password"]}
          value={values.password} onChange={set("password")} error={errors.password} helper="At least 8 characters. Give it to them in person." />
      )}
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>{busy ? "Saving..." : user ? "Save user" : "Add user"}</button>
        <button className="button button-secondary" type="button" onClick={onClose}>Cancel</button>
      </div>
    </Dialog>
  )
}

/** A new password for someone who forgot theirs; it signs them out everywhere. */
function PasswordDialog({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: () => void }) {
  const [password, setPassword] = useState("")
  const [error, setError] = useState("")
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password.length < limits["user.passwordMin"]) return setError(`At least ${limits["user.passwordMin"]} characters.`)
    setError("")
    setBusy(true)
    setProblem("")
    try {
      await staffApi(`/admin/users/${user.id}/password`, json("PUT", { password }))
      onSaved()
    } catch (reason) {
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  return (
    <Dialog title={`New password for ${user.name ?? user.username}`} titleId="reset-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      <TextField label="New password" name="password" type="password" autoComplete="new-password" maxLength={limits["user.password"]}
        value={password} onChange={(e) => setPassword(e.target.value)} error={error}
        helper="Give it to them in person or by phone. They are signed out everywhere." />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>{busy ? "Saving..." : "Set password"}</button>
        <button className="button button-secondary" type="button" onClick={onClose}>Cancel</button>
      </div>
    </Dialog>
  )
}
