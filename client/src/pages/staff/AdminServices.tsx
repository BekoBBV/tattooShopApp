import { useState, type FormEvent } from "react"
import { Plus } from "lucide-react"
import { Button, Eyebrow, Heading, Input, Select } from "../../components/ui"
import { ListToolbar, Pager } from "../../components/ListControls"
import { formatMoney } from "../../lib/data"
import { useListQuery, type Page } from "../../lib/paging"
import type { AdminService } from "../../lib/types"
import {
  palettes,
  send,
  useAction,
  useStaffLoad,
} from "../../features/staff/staffUtils"
import { Feedback, Loading } from "../../features/staff/StaffUi"
import { limits } from "../../lib/limits"

/** Admin: add, edit, hide and show services (/admin/services). */
type ServiceDraft = Pick<
  AdminService,
  "name" | "description" | "price" | "label" | "palette"
>
const emptyService: ServiceDraft = {
  name: "",
  description: "",
  price: 28,
  label: "",
  palette: "blue",
}

export function AdminServices() {
  const list = useListQuery({
    sort: "order,asc",
    filters: { active: "", minPrice: "", maxPrice: "" },
  })
  const load = useStaffLoad<Page<AdminService>>(`/admin/services?${list.query}`)
  const { error, reload } = load
  const items = load.data?.items
  const [draft, setDraft] = useState(emptyService)
  const [editing, setEditing] = useState("")
  const action = useAction()
  const fieldError = action.fieldErrors
  async function save(event: FormEvent) {
    event.preventDefault()
    const ok = await action.run(
      "save",
      () =>
        send(
          editing ? `/admin/services/${editing}` : "/admin/services",
          editing ? "PUT" : "POST",
          draft,
        ),
      editing ? "Service saved." : "Service added to the booking page.",
    )
    if (ok) {
      setDraft(emptyService)
      setEditing("")
      reload()
    }
  }
  const setActive = (item: AdminService, active: boolean) =>
    action
      .run(
        item.id,
        // Only the shown / hidden flag, so nothing else is overwritten with what this list loaded
        () => send(`/admin/services/${item.id}`, "PATCH", { active }),
        active
          ? `${item.name} is bookable again.`
          : `${item.name} is hidden from the booking page.`,
      )
      .then((ok) => ok && reload())
  const remove = (item: AdminService) => {
    if (!window.confirm(`Delete ${item.name} for good? Past bookings keep its name and price.`)) return
    action
      .run(item.id, () => send(`/admin/services/${item.id}`, "DELETE"), `${item.name} was deleted.`)
      .then((ok) => ok && reload())
  }
  return (
    <div className="dashboard-content">
      <div className="dashboard-heading">
        <div>
          <Eyebrow>Cut list</Eyebrow>
          <Heading level={1}>Services.</Heading>
        </div>
      </div>
      <form className="details-form admin-form" onSubmit={save}>
        <Heading level={3}>{editing ? "Edit service" : "Add service"}</Heading>
        <Input
          label="Name"
          required
          maxLength={limits["service.name"]}
          value={draft.name}
          error={fieldError.name}
          onChange={(e) => setDraft({ ...draft, name: e.target.value })}
        />
        <Input
          label="Description"
          maxLength={limits["service.description"]}
          value={draft.description}
          error={fieldError.description}
          onChange={(e) => setDraft({ ...draft, description: e.target.value })}
        />
        <Input
          label="Price EUR"
          type="number"
          min={0}
          max={1000}
          step={0.01}
          required
          value={draft.price}
          error={fieldError.price}
          onChange={(e) => setDraft({ ...draft, price: Number(e.target.value) })}
        />
        <Input
          label="Label"
          maxLength={limits["service.label"]}
          placeholder="MD 06"
          value={draft.label}
          error={fieldError.label}
          onChange={(e) => setDraft({ ...draft, label: e.target.value })}
        />
        <Select
          label="Palette"
          value={draft.palette}
          onChange={(e) => setDraft({ ...draft, palette: e.target.value })}
        >
          {palettes.map((item) => (
            <option key={item}>{item}</option>
          ))}
        </Select>
        <Feedback error={action.error} notice={action.notice} />
        <div className="row-actions">
          <Button type="submit" loading={action.busy === "save"}>
            <Plus /> {editing ? "Save service" : "Add service"}
          </Button>
          {editing ? (
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setEditing("")
                setDraft(emptyService)
                action.clear()
              }}
            >
              Cancel edit
            </Button>
          ) : null}
        </div>
      </form>
      <Heading level={2}>All services</Heading>
      <ListToolbar
        list={list}
        searchLabel="Search services"
        placeholder="Name, description, label…"
        sorts={[
          ["order,asc", "Menu order"],
          ["name,asc", "Name A–Z"],
          ["price,asc", "Price: low to high"],
          ["price,desc", "Price: high to low"],
        ]}
      >
        <Select
          label="Shown"
          value={list.filters.active}
          onChange={(e) => list.setFilter("active", e.target.value)}
        >
          <option value="">All</option>
          <option value="true">Bookable</option>
          <option value="false">Hidden</option>
        </Select>
        <Input
          label="Min price"
          type="number"
          min={0}
          value={list.filters.minPrice}
          onChange={(e) => list.setFilter("minPrice", e.target.value)}
        />
        <Input
          label="Max price"
          type="number"
          min={0}
          value={list.filters.maxPrice}
          onChange={(e) => list.setFilter("maxPrice", e.target.value)}
        />
      </ListToolbar>
      {!items ? (
        <Loading error={error} />
      ) : !items.length ? (
        <p className="account-empty">No services match this search.</p>
      ) : (
        <div className="booking-table">
          {items.map((item) => (
            <article key={item.id}>
              <div>
                <Heading level={3}>
                  {item.name} · {formatMoney(item.price)}
                </Heading>
                <p>
                  {[item.label, item.palette, item.description]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </div>
              {item.active ? null : <span className="status">Hidden</span>}
              <div className="row-actions">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditing(item.id)
                    setDraft({
                      name: item.name,
                      description: item.description,
                      price: item.price,
                      label: item.label,
                      palette: item.palette,
                    })
                    action.clear()
                    window.scrollTo({ top: 0, behavior: "smooth" })
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="ghost"
                  loading={action.busy === item.id}
                  onClick={() => setActive(item, !item.active)}
                >
                  {item.active ? "Hide" : "Show again"}
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
      <Pager data={load.data} list={list} noun="service" />
    </div>
  )
}
