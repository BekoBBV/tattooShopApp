// @vitest-environment jsdom
import { cleanup, configure, render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { createMemoryRouter, RouterProvider } from "react-router"
import { afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest"
import { routes } from "../app/routes"
import { installMockApi } from "../mock/mockApi"

/**
 * The real pages, run against the sample data (the same pretend server as `npm run dev:mock`):
 * a visitor's main journeys from the first click to the last page. The sample data lives for the
 * whole file, so each test uses its own design, artist and phone number.
 */

beforeAll(() => {
  // What the browser has and the test window lacks
  window.scrollTo = () => {}
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia
  configure({ asyncUtilTimeout: 5000 })
  installMockApi()
})

beforeEach(() => {
  // A new visitor: no booking in progress, nobody signed in
  localStorage.clear()
  sessionStorage.clear()
})

afterEach(cleanup)

/** Opens the website at `path`, as if typed into the address bar. */
function open(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return { router, user: userEvent.setup() }
}

const pageTitle = (name: string) => screen.findByRole("heading", { level: 1, name })

type User = ReturnType<typeof userEvent.setup>

/** Step 2: the first free time on the first open day, then Continue. */
async function pickFirstTime(user: User) {
  const times = await screen.findAllByRole("button", { name: /^\d\d:\d\d$/ })
  await user.click(times[0])
  await user.click(screen.getByRole("button", { name: "Continue" }))
}

/** Step 3: who is coming. */
async function fillDetails(user: User, phone: string) {
  await pageTitle("Your details")
  await user.type(screen.getByLabelText("Full name"), "Test Customer")
  await user.type(screen.getByLabelText("Phone"), phone)
  await user.type(screen.getByLabelText("Email"), "test@example.com")
  await user.type(screen.getByLabelText("Date of birth"), "04.04.1990")
}

/** A consultation made straight through the API, for the tests that start with a booking. */
async function bookedConsultation(artistId: string, phone: string): Promise<string> {
  const availability = await (await fetch(`/api/availability?artistId=${artistId}&days=14`)).json()
  const day = availability.days.find((d: { slots: unknown[] }) => d.slots.length > 0)
  const form = new FormData()
  form.append(
    "booking",
    new Blob(
      [
        JSON.stringify({
          artistId,
          date: day.date,
          time: day.slots[0].time,
          customerName: "Test Customer",
          phone,
          email: "test@example.com",
          dateOfBirth: "1990-04-04",
          idea: "A crane over waves on the shoulder.",
          placement: "FOREARM",
          sizeCm: 12,
          colour: "NOT_SURE",
          adult: true,
          consentForm: true,
        }),
      ],
      { type: "application/json" },
    ),
  )
  const answer = await fetch("/api/bookings/consultation", { method: "POST", body: form })
  expect(answer.status).toBe(201)
  return (await answer.json()).code
}

describe("booking", () => {
  it("books a flash design from the flash sheet through to the deposit", async () => {
    const { user } = open("/flash")
    await user.click(await screen.findByRole("link", { name: "Book Koi and wave" }))

    // Book goes straight to the time step, with the design in "Your booking"
    await pageTitle("Choose a time")
    const summary = await screen.findByRole("complementary", { name: "Your booking" })
    await within(summary).findByText("Koi and wave")
    within(summary).getByText("€220")
    within(summary).getByText("€190") // the price less the €30 deposit
    await pickFirstTime(user)

    await fillDetails(user, "0888 700 701")
    await user.click(screen.getByRole("checkbox", { name: /18 or older/ }))
    await user.click(screen.getByRole("checkbox", { name: /consent form/ }))
    await user.click(screen.getByRole("checkbox", { name: /deposit policy/ }))
    await user.click(screen.getByRole("button", { name: "Continue" }))

    await pageTitle("Pay the deposit")
    await screen.findByRole("button", { name: "Pay €30 deposit with Revolut" })
    screen.getByText("Koi and wave")
    screen.getByText(/holding this time for you/)
  }, 30_000)

  it("refuses to continue until the three confirmations are ticked", async () => {
    const { user } = open("/book/time?flash=oni")
    await pageTitle("Choose a time")
    await pickFirstTime(user)
    await fillDetails(user, "0888 700 702")
    await user.click(screen.getByRole("button", { name: "Continue" }))

    await screen.findByText("Please confirm that you are 18 or older.")
    screen.getByText("Please read and agree to the consent form.")
    screen.getByText("Please confirm the deposit policy.")
    screen.getByRole("heading", { level: 1, name: "Your details" })
  }, 30_000)

  it("lets the header's Book button pick a design in the Choose step", async () => {
    const { user } = open("/book/choose")
    const designs = await screen.findByRole("group", { name: "Choose a design" })
    await user.click(await within(designs).findByRole("radio", { name: /Small wave/ }))

    // The slip now shows the chosen design, and Continue leads to its times
    await screen.findByRole("heading", { level: 3, name: "Small wave" })
    await user.click(screen.getByRole("button", { name: "Continue" }))
    await pageTitle("Choose a time")
    await within(await screen.findByRole("complementary", { name: "Your booking" })).findByText("Small wave")
  }, 30_000)

  it("books a free consultation with a chosen artist", async () => {
    const { user } = open("/book/choose")
    const artists = await screen.findByRole("group", { name: "Choose your artist" })
    await user.click(await within(artists).findByRole("radio", { name: /Dario/ }))
    await user.click(screen.getByRole("button", { name: "Continue" }))

    await pageTitle("Choose a time")
    const summary = await screen.findByRole("complementary", { name: "Your booking" })
    within(summary).getByText("Free consultation")
    within(summary).getByText("Free")
    await pickFirstTime(user)

    await fillDetails(user, "0888 700 703")
    await user.type(screen.getByLabelText("Your idea"), "A hannya mask on the upper arm, black and grey.")
    await user.click(screen.getByRole("checkbox", { name: /18 or older/ }))
    await user.click(screen.getByRole("checkbox", { name: /consent form/ }))
    await user.click(screen.getByRole("button", { name: "Book consultation" }))

    await pageTitle("Your consultation is booked")
  }, 30_000)
})

describe("managing a booking", () => {
  it("finds a booking by its code and phone, and cancels it", async () => {
    const code = await bookedConsultation("nika", "0888 700 704")
    const { user } = open("/manage")
    await pageTitle("Manage a booking")
    await user.type(screen.getByLabelText("Booking code"), code)
    await user.type(screen.getByLabelText("Phone number"), "0888 700 704")
    await user.click(screen.getByRole("button", { name: "Find booking" }))

    await screen.findByRole("heading", { level: 2, name: "Consultation with Nika" })
    await user.click(screen.getByRole("button", { name: "Cancel booking" }))
    const dialog = await screen.findByRole("dialog", { name: "Cancel this booking?" })
    await user.click(within(dialog).getByRole("button", { name: "Cancel booking" }))

    await screen.findByText("Cancelled")
    expect(screen.queryByRole("button", { name: "Cancel booking" })).toBeNull()
  }, 30_000)

  it("says so when the code and phone don't match a booking", async () => {
    const { user } = open("/manage")
    await pageTitle("Manage a booking")
    await user.type(screen.getByLabelText("Booking code"), "ZZZZ-ZZ")
    await user.type(screen.getByLabelText("Phone number"), "0888 700 705")
    await user.click(screen.getByRole("button", { name: "Find booking" }))

    const problem = await screen.findByRole("alert")
    expect(problem.textContent).toMatch(/booking/i)
    expect(screen.queryByRole("button", { name: "Cancel booking" })).toBeNull()
  }, 30_000)
})

describe("signing in", () => {
  it("takes a customer to My account", async () => {
    const { user } = open("/account/sign-in")
    await pageTitle("Sign in")
    await user.type(screen.getByLabelText("Email"), "mira@example.com")
    await user.type(screen.getByLabelText("Password"), "any-password-1")
    await user.click(screen.getByRole("button", { name: "Sign in" }))

    await pageTitle("My account")
  }, 30_000)

  it("takes an artist to their calendar and keeps them out of the admin pages", async () => {
    const { user, router } = open("/staff/sign-in")
    await pageTitle("Staff sign-in")
    await user.type(screen.getByLabelText("Username"), "kai")
    await user.type(screen.getByLabelText("Password"), "any-password-1")
    await user.click(screen.getByRole("button", { name: "Sign in" }))
    await pageTitle("My calendar")

    await router.navigate("/admin/bookings")
    await waitFor(() => expect(router.state.location.pathname).toBe("/staff/calendar"))
    await pageTitle("My calendar")
  }, 30_000)

  it("takes the admin to the bookings list", async () => {
    const { user, router } = open("/staff/sign-in")
    await pageTitle("Staff sign-in")
    await user.type(screen.getByLabelText("Username"), "admin")
    await user.type(screen.getByLabelText("Password"), "any-password-1")
    await user.click(screen.getByRole("button", { name: "Sign in" }))

    await pageTitle("Bookings")
    expect(router.state.location.pathname).toBe("/admin/bookings")
  }, 30_000)

  it("turns a customer away from the staff sign-in", async () => {
    const { user, router } = open("/staff/sign-in")
    await pageTitle("Staff sign-in")
    await user.type(screen.getByLabelText("Username"), "mira@example.com")
    await user.type(screen.getByLabelText("Password"), "any-password-1")
    await user.click(screen.getByRole("button", { name: "Sign in" }))

    await screen.findByText(/This is a customer account/)
    expect(router.state.location.pathname).toBe("/staff/sign-in")
  }, 30_000)

  it("sends a signed-out visitor from the staff area to the staff sign-in", async () => {
    const { router } = open("/staff/calendar")
    await pageTitle("Staff sign-in")
    expect(router.state.location.pathname).toBe("/staff/sign-in")
  }, 30_000)
})
