import { ImagePlus } from "lucide-react"
import { useMemo, useState, type FormEvent } from "react"
import { ApiError } from "../lib/api"
import { hours } from "../lib/format"
import { limits } from "../lib/limits"
import { json, multipart, PICTURE_TYPES, pictureProblem, staffApi, usePreviewUrls } from "../lib/staff"
import { flashStateLabel } from "../lib/staffText"
import type { AdminFlash, ArtistOption } from "../lib/types"
import { errorText } from "../lib/useLoad"
import { ConfirmDialog, StaffSwitch } from "./StaffUi"
import { Dialog, FormError, SelectField, TextField } from "./Studio"

const DURATIONS = Array.from({ length: 24 }, (_, i) => (i + 1) * 30).map((m) => ({ value: String(m), label: hours(m) }))

/**
 * Add or edit a flash design: name, size, time, price, whether it is on the sheet, and its picture.
 * Artists edit their own (/staff/flash); the admin edits anyone's and picks the artist for a new one.
 */
export function FlashDialog({
  flash,
  artists,
  onClose,
  onSaved,
  onDeleted,
}: {
  flash: AdminFlash | null
  /** Given for the admin: the artist picker for a new design. */
  artists?: ArtistOption[]
  onClose: () => void
  onSaved: (flash: AdminFlash, created: boolean) => void
  onDeleted: (flash: AdminFlash) => void
}) {
  const admin = !!artists
  const base = admin ? "/admin/flash" : "/staff/flash"
  const [values, setValues] = useState({
    artistId: flash?.artistId ?? artists?.find((a) => a.active)?.id ?? "",
    name: flash?.name ?? "",
    widthCm: flash ? String(flash.widthCm) : "",
    heightCm: flash ? String(flash.heightCm) : "",
    durationMinutes: String(flash?.durationMinutes ?? 120),
    price: flash ? String(flash.price) : "",
    available: flash?.available ?? true,
  })
  const [picture, setPicture] = useState<File | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [problem, setProblem] = useState("")
  const [busy, setBusy] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const set = (field: "artistId" | "name" | "widthCm" | "heightCm" | "durationMinutes" | "price") => (e: { target: { value: string } }) =>
    setValues({ ...values, [field]: e.target.value })

  const chosen = useMemo(() => [picture], [picture])
  const [preview] = usePreviewUrls(chosen)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found: Record<string, string> = {}
    const width = Number(values.widthCm)
    const height = Number(values.heightCm)
    const price = Number(values.price)
    if (admin && !flash && !values.artistId) found.artistId = "Choose the artist."
    if (!values.name.trim()) found.name = "Give the design a name."
    if (!Number.isInteger(width) || width < 1 || width > 60) found.widthCm = "1 to 60 cm."
    if (!Number.isInteger(height) || height < 1 || height > 60) found.heightCm = "1 to 60 cm."
    if (!(price >= 1 && price <= 5000)) found.price = "€1 to €5000."
    setErrors(found)
    if (Object.keys(found).length) return
    const body = {
      ...(admin && !flash ? { artistId: values.artistId } : {}),
      name: values.name.trim(),
      widthCm: width,
      heightCm: height,
      durationMinutes: Number(values.durationMinutes),
      price,
      available: values.available,
    }
    setBusy(true)
    setProblem("")
    try {
      let saved: AdminFlash
      if (flash) {
        saved = await staffApi<AdminFlash>(`${base}/${flash.id}`, json("PUT", body))
        if (picture) saved = await staffApi<AdminFlash>(`${base}/${flash.id}/image`, multipart("PUT", { image: picture }))
      } else {
        saved = await staffApi<AdminFlash>(base, multipart("POST", { image: picture }, { name: "flash", value: body }))
      }
      onSaved(saved, !flash)
    } catch (reason) {
      if (reason instanceof ApiError && reason.errors) setErrors(reason.errors)
      setProblem(errorText(reason))
      setBusy(false)
    }
  }

  return (
    <Dialog title={flash ? "Edit flash design" : "Add flash design"} titleId="flash-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      {flash && flash.state !== "ON_SHEET" && flash.state !== "HIDDEN" && (
        <p className="dialog-hint">
          {flashStateLabel[flash.state]}
          {flash.bookingCode ? ` (booking ${flash.bookingCode})` : ""}. The booking keeps the name, size and price it was booked with.
        </p>
      )}
      {admin && !flash && (
        <SelectField label="Artist" name="artistId" value={values.artistId} onChange={set("artistId")} error={errors.artistId}
          options={artists!.filter((a) => a.active).map((a) => ({ value: a.id, label: a.name }))} />
      )}
      <TextField label="Name" name="name" maxLength={limits["flash.name"]} value={values.name} onChange={set("name")} error={errors.name} />
      <div className="panel-row dialog-row">
        <TextField label="Width in cm" name="widthCm" type="number" inputMode="numeric" min={1} max={60} value={values.widthCm}
          onChange={set("widthCm")} error={errors.widthCm} />
        <TextField label="Height in cm" name="heightCm" type="number" inputMode="numeric" min={1} max={60} value={values.heightCm}
          onChange={set("heightCm")} error={errors.heightCm} />
      </div>
      <div className="panel-row dialog-row">
        <SelectField label="Time" name="durationMinutes" options={DURATIONS} value={values.durationMinutes} onChange={set("durationMinutes")} />
        <TextField label="Price €" name="price" type="number" inputMode="decimal" min={1} value={values.price} onChange={set("price")} error={errors.price} />
      </div>
      <div className="dialog-picture">
        <div className="staff-image">{(preview ?? flash?.image?.thumbUrl) && <img src={preview ?? flash!.image!.thumbUrl} alt="" />}</div>
        <div>
          <label className="button button-secondary file-button staff-compact">
            <ImagePlus className="icon" size={18} aria-hidden="true" /> {flash?.image || picture ? "Replace picture" : "Add picture"}
            <input type="file" accept={PICTURE_TYPES} onChange={(e) => {
              const file = e.target.files?.[0] ?? null
              e.target.value = ""
              if (!file) return
              const why = pictureProblem(file)
              setErrors({ ...errors, picture: why ?? "" })
              if (!why) setPicture(file)
            }} />
          </label>
          {errors.picture ? <p className="field-error">{errors.picture}</p> : <p className="helper">JPG, PNG, HEIC or WebP, up to 10 MB.</p>}
        </div>
      </div>
      <StaffSwitch label="On the flash sheet" checked={values.available} onChange={(available) => setValues({ ...values, available })} />
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? "Saving..." : flash ? "Save design" : "Add design"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>
          Cancel
        </button>
        {flash && (
          <button className="staff-text-button dialog-delete" type="button" onClick={() => setDeleting(true)}>
            Delete design
          </button>
        )}
      </div>
      {deleting && flash && (
        <ConfirmDialog
          title={`Delete ${flash.name}?`}
          confirmLabel="Delete design"
          onClose={() => setDeleting(false)}
          onConfirm={async () => {
            await staffApi(`${base}/${flash.id}`, { method: "DELETE" })
            onDeleted(flash)
          }}
        >
          It disappears from the sheet and from this list. A design that is held or booked can't be deleted; take it off the sheet instead.
        </ConfirmDialog>
      )}
    </Dialog>
  )
}
