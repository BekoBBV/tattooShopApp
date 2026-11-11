import { ImagePlus, Plus } from "lucide-react"
import { useState, type FormEvent } from "react"
import { FlashDialog } from "../../components/FlashDialog"
import { Lightbox } from "../../components/Lightbox"
import { ConfirmDialog, StaffHeader, StaffState, StaffSwitch, StatusLine } from "../../components/StaffUi"
import { Dialog, FormError } from "../../components/Studio"
import { euros, hours, size } from "../../lib/format"
import { json, multipart, PICTURE_TYPES, pictureProblem, staffApi, usePreviewUrls, useStaff } from "../../lib/staff"
import { flashStateLabel } from "../../lib/staffText"
import type { AdminFlash, Artist, Image, Page, Work } from "../../lib/types"
import { errorText, useLoad } from "../../lib/useLoad"
import { usePageTitle } from "../../lib/usePageTitle"

/** My flash and work: the artist's designs, their portfolio photos and the signature piece on their scroll. */
export default function StaffFlash() {
  usePageTitle("My flash and work")
  const staff = useStaff()
  const token = staff?.token
  const flash = useLoad<Page<AdminFlash>>("/staff/flash?size=200&sort=order,asc", token)
  const works = useLoad<Work[]>("/staff/works", token)
  const artists = useLoad<Artist[]>("/artists")
  const me = artists.data?.find((a) => a.id === staff?.user.artistId) ?? null
  const [editing, setEditing] = useState<AdminFlash | "new" | null>(null)
  const [note, setNote] = useState("")
  const [problem, setProblem] = useState("")
  const [uploading, setUploading] = useState<File[] | null>(null)
  const [removing, setRemoving] = useState<Work | null>(null)
  const [viewing, setViewing] = useState<number | null>(null)
  const [signature, setSignature] = useState<Image | null | undefined>(undefined)
  const [removingSignature, setRemovingSignature] = useState(false)
  const shownSignature = signature === undefined ? me?.signaturePiece ?? null : signature

  const say = (text: string) => {
    setNote(text)
    setProblem("")
  }

  async function setAvailable(f: AdminFlash, available: boolean) {
    try {
      await staffApi<AdminFlash>(`/staff/flash/${f.id}`, json("PATCH", { active: available }))
      say(available ? `${f.name} is back on the flash sheet.` : `${f.name} is off the flash sheet.`)
      flash.reload()
    } catch (reason) {
      setProblem(errorText(reason))
    }
  }

  async function uploadSignature(file: File) {
    const why = pictureProblem(file)
    if (why) return setProblem(why)
    try {
      setSignature(await staffApi<Image>("/staff/signature", multipart("PUT", { image: file })))
      say("Signature piece updated. It shows on your scroll on the artists' pages.")
    } catch (reason) {
      setProblem(errorText(reason))
    }
  }

  const items = flash.data?.items ?? []
  return (
    <>
      <StaffHeader title="My flash and work">Manage designs customers can book and work shown on your profile.</StaffHeader>
      <StatusLine>{note}</StatusLine>
      <StatusLine error>{problem}</StatusLine>

      <section className="staff-section">
        <div className="staff-section-heading">
          <h2>My flash designs</h2>
          <button className="button button-primary staff-compact" type="button" onClick={() => setEditing("new")}>
            <Plus className="icon" size={18} aria-hidden="true" /> Add flash design
          </button>
        </div>
        <StaffState loading={flash.loading && !flash.data} error={flash.error} onRetry={flash.reload}
          empty={flash.data && items.length === 0 && "No designs yet. Add your first one."} />
        <div className="staff-flash-list">
          {items.map((f) => (
            <article key={f.id}>
              <div className="staff-image">{f.image && <img src={f.image.thumbUrl} alt="" />}</div>
              <div>
                <h3>{f.name}</h3>
                <p>
                  {size(f.widthCm, f.heightCm)}, {hours(f.durationMinutes)}, {euros(f.price)}
                </p>
                {f.state !== "ON_SHEET" && f.state !== "HIDDEN" && (
                  <p className="flash-state">
                    {flashStateLabel[f.state]}
                    {f.bookingCode ? `, booking ${f.bookingCode}` : ""}
                  </p>
                )}
              </div>
              <div className="flash-row-actions">
                <StaffSwitch label="Available" hiddenLabel={`${f.name} on the flash sheet`} checked={f.available}
                  onChange={(on) => setAvailable(f, on)} />
                <button className="staff-text-button" type="button" aria-label={`Edit ${f.name}`} onClick={() => setEditing(f)}>
                  Edit
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="staff-section">
        <div className="staff-section-heading">
          <h2>My work</h2>
          <label className="button button-secondary file-button staff-compact">
            <ImagePlus className="icon" size={18} aria-hidden="true" /> Upload photos
            <input type="file" multiple accept={PICTURE_TYPES} onChange={(e) => {
              const files = [...(e.target.files ?? [])]
              e.target.value = ""
              const bad = files.map(pictureProblem).find(Boolean)
              if (bad) return setProblem(bad)
              if (files.length) setUploading(files.slice(0, 10))
            }} />
          </label>
        </div>
        <StaffState loading={works.loading && !works.data} error={works.error} onRetry={works.reload}
          empty={works.data && works.data.length === 0 && "No photos yet. They show on your artist page."} />
        <div className="staff-portfolio-grid">
          {works.data?.map((w, i) => (
            <figure key={w.id}>
              <button type="button" className="portfolio-open" aria-label={`Open ${w.caption || `photo ${i + 1}`}`} onClick={() => setViewing(i)}>
                <img src={w.image.thumbUrl} alt="" />
              </button>
              <figcaption>{w.caption || "No caption"}</figcaption>
              <button className="staff-text-button" type="button" aria-label={`Remove ${w.caption || `photo ${i + 1}`}`} onClick={() => setRemoving(w)}>
                Remove
              </button>
            </figure>
          ))}
        </div>
      </section>

      <section className="staff-section">
        <div className="staff-section-heading">
          <h2>Signature piece</h2>
          <label className="button button-secondary file-button staff-compact">
            <ImagePlus className="icon" size={18} aria-hidden="true" /> {shownSignature ? "Replace picture" : "Add picture"}
            <input type="file" accept={PICTURE_TYPES} onChange={(e) => {
              const file = e.target.files?.[0]
              e.target.value = ""
              if (file) uploadSignature(file)
            }} />
          </label>
        </div>
        <div className="signature-setting">
          <div className="staff-image">{shownSignature && <img src={shownSignature.thumbUrl} alt="Your signature piece" />}</div>
          <div>
            <p>The piece shown on your hanging scroll on the artists' pages. Pick one that shows your style at a glance.</p>
            {shownSignature && (
              <button className="staff-text-button" type="button" onClick={() => setRemovingSignature(true)}>
                Remove picture
              </button>
            )}
          </div>
        </div>
      </section>

      {editing && (
        <FlashDialog
          flash={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(f, created) => {
            setEditing(null)
            say(created ? `${f.name} added${f.available ? " to the flash sheet" : ""}.` : `${f.name} saved.`)
            flash.reload()
          }}
          onDeleted={(f) => {
            setEditing(null)
            say(`${f.name} deleted.`)
            flash.reload()
          }}
        />
      )}
      {uploading && (
        <UploadDialog
          files={uploading}
          onClose={() => setUploading(null)}
          onDone={(count) => {
            setUploading(null)
            say(count === 1 ? "Photo added to your work." : `${count} photos added to your work.`)
            works.reload()
          }}
        />
      )}
      {removingSignature && (
        <ConfirmDialog
          title="Remove your signature piece?"
          confirmLabel="Remove picture"
          onClose={() => setRemovingSignature(false)}
          onConfirm={async () => {
            await staffApi("/staff/signature", { method: "DELETE" })
            setRemovingSignature(false)
            setSignature(null)
            say("Signature piece removed. Your scroll shows the studio seal until you add another.")
          }}
        >
          Your hanging scroll on the artists' pages goes back to the studio seal.
        </ConfirmDialog>
      )}
      {removing && (
        <ConfirmDialog
          title="Remove this photo?"
          confirmLabel="Remove photo"
          onClose={() => setRemoving(null)}
          onConfirm={async () => {
            await staffApi(`/staff/works/${removing.id}`, { method: "DELETE" })
            setRemoving(null)
            say("Photo removed.")
            works.reload()
          }}
        >
          {removing.caption ? `"${removing.caption}" disappears` : "It disappears"} from your artist page.
        </ConfirmDialog>
      )}
      {viewing !== null && works.data && (
        <Lightbox
          photos={works.data.map((w, i) => ({ image: w.image, caption: w.caption || `Photo ${i + 1}` }))}
          index={viewing}
          label="Your work"
          onChange={setViewing}
          onClose={() => setViewing(null)}
        />
      )}
    </>
  )
}

/** A caption for each chosen photo, then they are uploaded one by one. */
function UploadDialog({ files, onClose, onDone }: { files: File[]; onClose: () => void; onDone: (count: number) => void }) {
  const [captions, setCaptions] = useState(files.map(() => ""))
  const previews = usePreviewUrls(files)
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState("")
  const [sent, setSent] = useState(0)


  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setProblem("")
    let done = sent
    try {
      for (; done < files.length; done++) {
        const form = new FormData()
        form.append("image", files[done])
        if (captions[done].trim()) form.append("caption", captions[done].trim())
        await staffApi<Work>("/staff/works", { method: "POST", body: form })
        setSent(done + 1)
      }
      onDone(files.length)
    } catch (reason) {
      setProblem(`${done ? `${done} of ${files.length} uploaded. ` : ""}${errorText(reason)}`)
      setBusy(false)
    }
  }

  return (
    <Dialog title={files.length === 1 ? "Add a photo" : `Add ${files.length} photos`} titleId="upload-title" onClose={onClose} as="form" onSubmit={submit} closeButton>
      <FormError>{problem}</FormError>
      <div className="upload-list">
        {files.map((f, i) => (
          <label key={f.name + i} className={`field upload-item${i < sent ? " is-sent" : ""}`}>
            <span className="upload-thumb">{previews[i] && <img src={previews[i]} alt="" />}</span>
            <span>
              Caption (optional)
              <input value={captions[i]} maxLength={60} disabled={i < sent} placeholder="Tiger back piece"
                onChange={(e) => setCaptions(captions.map((c, j) => (j === i ? e.target.value : c)))} />
            </span>
          </label>
        ))}
      </div>
      <div className="dialog-actions">
        <button className="button button-primary" type="submit" disabled={busy}>
          {busy ? `Uploading ${sent + 1} of ${files.length}...` : "Upload"}
        </button>
        <button className="button button-secondary" type="button" onClick={onClose}>
          Cancel
        </button>
      </div>
    </Dialog>
  )
}
