import { useEffect, useState, type FocusEvent, type FormEvent } from "react"
import { useLocation } from "react-router"
import {
  ArrowRight,
  Clock3,
  MapPin,
  ShieldCheck,
  Sparkles,
  Star,
} from "lucide-react"
import {
  GraffitiBackdrop,
  MiniDisc,
  SiteFooter,
  SiteHeader,
  SprayDisc,
} from "../components/brand"
import {
  AppLink,
  Button,
  Eyebrow,
  Heading,
  Input,
  Select,
  Textarea,
} from "../components/ui"
import { ListToolbar, Pager } from "../components/ListControls"
import { allDays, dayNames } from "../features/staff/staffUtils"
import { ApiError, api } from "../lib/api"
import { useViewerToken } from "../lib/customer"
import { formatMoney, shop, telHref } from "../lib/data"
import { useListQuery, type Page } from "../lib/paging"
import { useLoad } from "../lib/useLoad"
import {
  checkMessage,
  checkReview,
  useFieldChecks,
  type FieldErrors,
} from "../lib/validation"
import type { Artist, Review, Service } from "../lib/types"
import { usePageTitle } from "../lib/usePageTitle"
import { limits } from "../lib/limits"

const initials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
// [src, alt] pairs; empty until the studio's own photos arrive (the artists' portfolios come in phase 4)
const gallery: [string, string][] = []
const dayOrder = [
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const
const dayLabel: Record<typeof dayOrder[number], string> = {
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
}

/** Submits a simple form to the API, tracking sending/sent state and showing server errors. */
function useFormSubmit(
  path: string,
  toBody: (data: FormData) => Record<string, unknown>,
  validate: (data: FormData) => FieldErrors,
) {
  const [clientErrors, setClientErrors] = useState<FieldErrors>({})
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const checks = useFieldChecks(clientErrors, fieldErrors)
  // Re-check on every change and when leaving a field, so a fixed field clears at once
  const recheck = (event: FormEvent<HTMLFormElement>) =>
    setClientErrors(validate(new FormData(event.currentTarget)))
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validate(new FormData(event.currentTarget))
    setClientErrors(errors)
    if (!checks.canSubmit(event.currentTarget, errors)) return
    if (sending) return
    setSending(true)
    setError("")
    setFieldErrors({})
    try {
      await api(path, {
        method: "POST",
        body: JSON.stringify(toBody(new FormData(event.currentTarget))),
      })
      setSent(true)
    } catch (reason) {
      if (reason instanceof ApiError) setFieldErrors(reason.errors || {})
      setError(
        reason instanceof Error ? reason.message : "Could not send. Try again.",
      )
    } finally {
      setSending(false)
    }
  }
  return {
    submit,
    sending,
    sent,
    error,
    errorFor: checks.errorFor,
    onBlur: (event: FocusEvent<HTMLFormElement>) => {
      recheck(event)
      checks.onBlur(event)
    },
    onChange: (event: FormEvent<HTMLFormElement>) => {
      recheck(event)
      checks.onChange(event)
    },
  }
}

/** 1–5 stars as radio buttons (keyboard: arrow keys), sent as "rating" with the review form */
function StarPicker({ error }: { error?: string }) {
  return (
    <fieldset
      className="star-picker"
      aria-invalid={error ? true : undefined}
      aria-describedby={error ? "rating-error" : undefined}
    >
      <legend>Stars</legend>
      <div className="star-picker-stars">
        {[5, 4, 3, 2, 1].map((value) => (
          <label key={value} title={`${value} star${value === 1 ? "" : "s"}`}>
            <input type="radio" name="rating" value={value} />
            <Star aria-hidden="true" />
            <span className="sr-only">
              {value} star{value === 1 ? "" : "s"}
            </span>
          </label>
        ))}
      </div>
      {error ? (
        <small className="field-error" id="rating-error">
          {error}
        </small>
      ) : null}
    </fieldset>
  )
}

export default function Home() {
  usePageTitle()
  // Signed in (customer or staff): the lists below can be searched, sorted and paged.
  // Guests see the plain lists (course rule: level 0 = read only).
  const viewer = useViewerToken()
  const serviceList = useListQuery({ sort: "order,asc", size: 6, filters: { minPrice: "", maxPrice: "" } })
  const serviceSearch = useLoad<Page<Service>>(viewer ? `/services/search?${serviceList.query}` : null, viewer)
  const artistList = useListQuery({ sort: "order,asc", size: 6, filters: { day: "" } })
  const artistSearch = useLoad<Page<Artist>>(viewer ? `/artists/search?${artistList.query}` : null, viewer)
  const reviewList = useListQuery({ sort: "date,desc", size: 4, filters: { minRating: "" } })
  const reviewSearch = useLoad<Page<Review>>(viewer ? `/reviews/search?${reviewList.query}` : null, viewer)
  // The plain service list is only needed by guests; every visitor needs all artists (hero, opening
  // hours) and the review summary (average rating)
  const servicesLoad = useLoad<Service[]>(viewer ? null : "/services")
  const artistsLoad = useLoad<Artist[]>("/artists")
  const reviewsLoad = useLoad<{
    averageRating: number
    count: number
    reviews: Review[]
  }>("/reviews")
  const artists = artistsLoad.data || []
  const reviewData = reviewsLoad.data
  // Signed in, the services section waits for the first search answer instead of the plain list
  const servicesWaiting = viewer ? !serviceSearch.data && !serviceSearch.error : servicesLoad.loading
  const servicesError = viewer ? (serviceSearch.data ? "" : serviceSearch.error) : servicesLoad.error
  const loading = servicesWaiting || artistsLoad.loading || reviewsLoad.loading
  const error = servicesError || artistsLoad.error || reviewsLoad.error
  function retry() {
    if (viewer) serviceSearch.reload()
    else servicesLoad.reload()
    artistsLoad.reload()
    reviewsLoad.reload()
  }
  const shownServices = (viewer ? serviceSearch.data?.items : servicesLoad.data) ?? []
  const shownArtists = viewer ? (artistSearch.data?.items ?? artists) : artists
  const shownReviews = viewer ? (reviewSearch.data?.items ?? reviewData?.reviews) : reviewData?.reviews
  const reviewForm = useFormSubmit(
    "/reviews",
    (data) => ({
      name: data.get("name"),
      rating: Number(data.get("rating")),
      text: data.get("text"),
    }),
    checkReview,
  )
  const messageForm = useFormSubmit(
    "/messages",
    (data) => ({
      name: data.get("name"),
      contact: data.get("contact"),
      text: data.get("text"),
    }),
    checkMessage,
  )
  // Links like "Meet the crew" (/#artists): scroll there on every click, also when already on this
  // page, and only once the sections have loaded (they appear after the shop data arrives)
  const { hash, key } = useLocation()
  useEffect(() => {
    if (!hash || loading) return
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
    document
      .getElementById(hash.slice(1))
      ?.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" })
  }, [hash, key, loading])
  return (
    <main>
      <section className="hero">
        <GraffitiBackdrop />
        <SiteHeader />
        <div className="hero-grid wrap">
          <div className="hero-copy">
            <Eyebrow>Plovdiv cuts, remixed</Eyebrow>
            <Heading level={1}>
              Sharp cuts.
              <br />
              <span>Loud style.</span>
            </Heading>
            <p className="hero-lede">
              A neighborhood tattoo studio built for clean lines, good energy and
              cuts that hit different.
            </p>
            <div className="hero-actions">
              <AppLink to="/book/service" variant="primary">
                Book your chair <ArrowRight />
              </AppLink>
              <AppLink to="/#artists" variant="secondary">
                Meet the crew
              </AppLink>
            </div>
            <div className="hero-meta">
              <span>
                <MapPin /> Plovdiv, BG
              </span>
              <span>
                <Clock3 /> Open Tue–Sun
              </span>
            </div>
          </div>
          <div
            className="hero-art"
            aria-label="Abstract spray can cap collection"
          >
            {artists.map((artist, index) => (
              <div
                className={`hero-disc hero-disc-${index + 1}`}
                key={artist.id}
              >
                <SprayDisc accent={artist.accent} size="lg" />
              </div>
            ))}
            <span className="hero-sticker">
              WALK IN
              <br />
              STAND OUT
            </span>
          </div>
        </div>
      </section>
      {loading ? (
        <section className="section">
          <div className="loading-panel">
            <div className="spinner" />
            <p>Loading the shop…</p>
          </div>
        </section>
      ) : null}
      {error ? (
        <section className="section">
          <div className="error-panel">
            <Heading level={3}>The signal dropped.</Heading>
            <p>{error}</p>
            <Button onClick={retry}>Try again</Button>
          </div>
        </section>
      ) : null}
      {!loading && !error ? (
        <>
          <section className="section services-section" id="services">
            <div className="wrap">
              <div className="section-heading">
                <div>
                  <Eyebrow>Pick your track</Eyebrow>
                  <Heading level={2}>The cut list</Heading>
                </div>
                <p>
                  Every service starts with a real consultation and ends with
                  the details dialed in.
                </p>
              </div>
              {viewer ? (
                <ListToolbar
                  list={serviceList}
                  searchLabel="Search cuts"
                  placeholder="Fade, beard…"
                  sorts={[
                    ["order,asc", "Menu order"],
                    ["price,asc", "Price: low to high"],
                    ["price,desc", "Price: high to low"],
                    ["name,asc", "Name A–Z"],
                  ]}
                >
                  <Input
                    label="Min price €"
                    type="number"
                    min={0}
                    value={serviceList.filters.minPrice}
                    onChange={(e) => serviceList.setFilter("minPrice", e.target.value)}
                  />
                  <Input
                    label="Max price €"
                    type="number"
                    min={0}
                    value={serviceList.filters.maxPrice}
                    onChange={(e) => serviceList.setFilter("maxPrice", e.target.value)}
                  />
                </ListToolbar>
              ) : null}
              {shownServices.length ? (
                <div className="service-grid">
                  {shownServices.map((service) => (
                    <MiniDisc
                      key={service.id}
                      className={`palette-${service.palette}`}
                      label={service.label}
                    >
                      <div className="disc-window">
                        <span />
                      </div>
                      <div className="disc-copy">
                        <Heading level={3}>{service.name}</Heading>
                        <p>{service.description}</p>
                        <div>
                          <span>One-hour slot</span>
                          <strong>{formatMoney(service.price)}</strong>
                        </div>
                      </div>
                    </MiniDisc>
                  ))}
                </div>
              ) : (
                <div className="empty-state">
                  <Heading level={3}>No cuts on the list.</Heading>
                  <p>{viewer ? "Nothing matches this search." : "Please check back soon."}</p>
                </div>
              )}
              {viewer ? <Pager data={serviceSearch.data} list={serviceList} noun="cut" /> : null}
            </div>
          </section>
          <section className="section artist-section" id="artists">
            <div className="wrap">
              <div className="section-heading">
                <div>
                  <Eyebrow>Six chairs, one frequency</Eyebrow>
                  <Heading level={2}>Meet the crew</Heading>
                </div>
                <p>
                  Different signatures. Same standard. Every artist offers every
                  service.
                </p>
              </div>
              {viewer ? (
                <ListToolbar
                  list={artistList}
                  searchLabel="Search the crew"
                  placeholder="Name, style, @handle…"
                  sorts={[
                    ["order,asc", "Shop order"],
                    ["name,asc", "Name A–Z"],
                    ["name,desc", "Name Z–A"],
                  ]}
                >
                  <Select
                    label="Works on"
                    value={artistList.filters.day}
                    onChange={(e) => artistList.setFilter("day", e.target.value)}
                  >
                    <option value="">Any day</option>
                    {allDays.map((day) => (
                      <option key={day} value={day}>
                        {dayNames[day]}
                      </option>
                    ))}
                  </Select>
                </ListToolbar>
              ) : null}
              {viewer && !shownArtists.length ? (
                <div className="empty-state">
                  <p>Nobody matches this search.</p>
                </div>
              ) : null}
              <div className="artist-grid">
                {shownArtists.map((artist) => (
                  <article
                    className={`artist-card accent-${artist.accent}`}
                    key={artist.id}
                  >
                    <SprayDisc
                      accent={artist.accent}
                      initials={initials(artist.name)}
                      size="lg"
                    />
                    <span className="artist-handle">{artist.handle}</span>
                    <Heading level={3}>{artist.name}</Heading>
                    <strong>{artist.specialty}</strong>
                    <p>{artist.bio}</p>
                    <AppLink
                      to={`/book/artist?artist=${artist.id}`}
                      className="text-link"
                    >
                      Book with {artist.name.split(" ")[0]} <ArrowRight />
                    </AppLink>
                  </article>
                ))}
              </div>
              {viewer ? <Pager data={artistSearch.data} list={artistList} noun="artist" /> : null}
            </div>
          </section>
          <section className="section how-section">
            <div className="wrap">
              <Eyebrow>Easy in, fresh out</Eyebrow>
              <Heading level={2}>Four steps. Zero hassle.</Heading>
              <div className="steps-row">
                {[
                  [
                    "01",
                    "Choose a cut",
                    "Pick the service that fits the reset.",
                  ],
                  [
                    "02",
                    "Find your artist",
                    "Choose your favorite or any open chair.",
                  ],
                  ["03", "Lock the time", "See seven days in shop time."],
                  ["04", "Show up", "We’ll hold the chair. Pay at the shop."],
                ].map(([n, t, x]) => (
                  <div className="how-step" key={n}>
                    <span>{n}</span>
                    <Heading level={3}>{t}</Heading>
                    <p>{x}</p>
                  </div>
                ))}
              </div>
            </div>
          </section>
          <section className="section reviews-section">
            <div className="wrap split-section">
              <div>
                <Eyebrow>Word on the street</Eyebrow>
                <Heading level={2}>
                  {reviewData?.averageRating.toFixed(1)} / 5
                </Heading>
                <p>{reviewData?.count} approved reviews</p>
                {viewer ? (
                  <ListToolbar
                    list={reviewList}
                    searchLabel="Search reviews"
                    placeholder="Name or words…"
                    sorts={[
                      ["date,desc", "Newest first"],
                      ["rating,desc", "Most stars"],
                      ["rating,asc", "Fewest stars"],
                      ["date,asc", "Oldest first"],
                    ]}
                  >
                    <Select
                      label="Stars"
                      value={reviewList.filters.minRating}
                      onChange={(e) => reviewList.setFilter("minRating", e.target.value)}
                    >
                      <option value="">Any</option>
                      <option value="5">5 only</option>
                      <option value="4">4 or more</option>
                      <option value="3">3 or more</option>
                    </Select>
                  </ListToolbar>
                ) : null}
                <div className="review-list">
                  {viewer && shownReviews && !shownReviews.length ? (
                    <p>No reviews match this search.</p>
                  ) : null}
                  {shownReviews?.map((review) => (
                    <article key={review.id}>
                      <span>
                        {Array.from({ length: review.rating }, (_, i) => (
                          <Star key={i} />
                        ))}
                      </span>
                      <p>“{review.text}”</p>
                      <strong>{review.name}</strong>
                    </article>
                  ))}
                </div>
                {viewer ? <Pager data={reviewSearch.data} list={reviewList} noun="review" /> : null}
              </div>
              <form
                className="details-form"
                noValidate
                onSubmit={reviewForm.submit}
                onBlur={reviewForm.onBlur}
                onChange={reviewForm.onChange}
              >
                {reviewForm.sent ? (
                  <div className="success-panel">
                    <Heading level={3}>Thanks!</Heading>
                    <p>Your review will appear after approval.</p>
                  </div>
                ) : (
                  <>
                    <Heading level={3}>Leave a review</Heading>
                    <Input
                      name="name"
                      label="Name"
                      required
                      maxLength={limits["review.name"]}
                      error={reviewForm.errorFor("name")}
                    />
                    <StarPicker error={reviewForm.errorFor("rating")} />
                    <Textarea
                      name="text"
                      label="Your review"
                      rows={4}
                      required
                      maxLength={limits["review.text"]}
                      error={reviewForm.errorFor("text")}
                    />
                    {reviewForm.error ? (
                      <p className="form-error">{reviewForm.error}</p>
                    ) : null}
                    <Button type="submit" loading={reviewForm.sending}>
                      Send review
                    </Button>
                  </>
                )}
              </form>
            </div>
          </section>
          {gallery.length ? (
            <section className="section gallery-section">
              <div className="wrap">
                <Eyebrow>From the studio</Eyebrow>
                <Heading level={2}>Tattoo Studio frames.</Heading>
                <div className="gallery-grid">
                  {gallery.map(([src, alt]) => (
                    <img key={src} src={src} alt={alt} loading="lazy" />
                  ))}
                </div>
              </div>
            </section>
          ) : null}
          <section className="section visit-section">
            <div className="wrap visit-grid">
              <div>
                <Eyebrow>Come through</Eyebrow>
                <Heading level={2}>Your next cut lives here.</Heading>
                <p>{shop.address}</p>
                <p>
                  <a href={telHref(shop.phone)}>{shop.phone}</a>
                  <br />
                  <a href={`mailto:${shop.email}`}>{shop.email}</a>
                </p>
                <AppLink to="/book/service" variant="primary">
                  Book your chair <ArrowRight />
                </AppLink>
              </div>
              <div className="visit-panel">
                <div className="shop-fs-art">
                  <img
                    src="/tattoo-studio-graffiti-logo.webp"
                    alt="Tattoo Studio graffiti logo"
                  />
                </div>
                <div className="visit-panel-copy">
                  <Sparkles className="visit-icon" />
                  <Heading level={3}>Shop hours</Heading>
                  <dl>
                    <div>
                      <dt>Mon</dt>
                      <dd>Closed</dd>
                    </div>
                    {dayOrder.map((day) => {
                      const hours = artists
                        .flatMap((artist) => artist.weeklyHours)
                        .find((item) => item.day === day)
                      return (
                        <div key={day}>
                          <dt>{dayLabel[day]}</dt>
                          <dd>
                            {hours ? `${hours.start}–${hours.end}` : "Closed"}
                          </dd>
                        </div>
                      )
                    })}
                  </dl>
                  <span>
                    <ShieldCheck /> Online cancellation is free until 2 hours
                    before.
                  </span>
                </div>
              </div>
            </div>
          </section>
          <section className="section contact-section">
            <div className="wrap split-section">
              <div>
                <Eyebrow>Talk to us</Eyebrow>
                <Heading level={2}>Drop us a line.</Heading>
                <p>
                  Questions about the shop or your next cut? Send a message.
                </p>
              </div>
              <form
                className="details-form"
                noValidate
                onSubmit={messageForm.submit}
                onBlur={messageForm.onBlur}
                onChange={messageForm.onChange}
              >
                {messageForm.sent ? (
                  <div className="success-panel">
                    <Heading level={3}>Message sent</Heading>
                  </div>
                ) : (
                  <>
                    <Input
                      name="name"
                      label="Name"
                      required
                      maxLength={limits["message.name"]}
                      error={messageForm.errorFor("name")}
                    />
                    <Input
                      name="contact"
                      label="Email or phone"
                      required
                      maxLength={limits["message.contact"]}
                      error={messageForm.errorFor("contact")}
                    />
                    <Textarea
                      name="text"
                      label="Message"
                      rows={5}
                      required
                      maxLength={limits["message.text"]}
                      error={messageForm.errorFor("text")}
                    />
                    {messageForm.error ? (
                      <p className="form-error">{messageForm.error}</p>
                    ) : null}
                    <Button type="submit" loading={messageForm.sending}>
                      Send message
                    </Button>
                  </>
                )}
              </form>
            </div>
          </section>
        </>
      ) : null}
      <SiteFooter />
    </main>
  )
}
