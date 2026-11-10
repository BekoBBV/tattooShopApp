# Design: Blade & Brush (Night ink)

Phase 2 result: the name, the look and every screen, on desktop, tablet and phone. The designs are in the Figma file [Blade & Brush — Night ink](https://www.figma.com/design/DUYYjyej8cFLeuGVsY4EBW) (cover, design system, 31 desktop, 24 tablet and 24 phone screens in auto layout, linked as a clickable prototype) and on the [Superdesign canvas](https://superdesign.dev/teams/8ec3288e-9c5a-4a88-8731-efaeb2ddbf11/projects/e0468592-9861-4477-b38c-86058735e3b9), where they were made. This page records the decisions; the customer website was built from them in phase 4 (see [The website](#the-website-phase-4)).

**Blade & Brush** is a fictional traditional Japanese tattoo studio in central Sofia, open Tuesday to Sunday, 11:00 to 23:00, closed on Monday. Three artists: Kai (bold traditional colour), Dario (black and grey, ink wash) and Nika (fine line). All text is in English.

## How the look was chosen

1. The first brief ([figma-make-prompt-blade-and-brush.md](figma-make-prompt-blade-and-brush.md)) produced a Figma Make prototype on light washi paper.
2. Three variations of the home page were compared. **Night ink** was chosen: the same paper, ink and red, but the page is black and everything people read or fill in sits on a sheet of paper pinned to it.
3. A design critique led to fixes: visible keyboard focus, stronger input borders, 44px targets, 14px minimum text, the night painting on every page banner, a slim footer during booking, and the missing dialogs.
4. A review in November 2026 checked every page at 1440, 1024, 834, 768 and 390px and fixed what it found:
   - The header links overlapped the logo between 768 and 1023px: tablets now use the menu button, like phones.
   - On a 1440 × 900 laptop the hero painting pushed both buttons below the first screen: the painting's height now leaves room for the title and buttons.
   - Artist seals on black were a red outline letter (3.2:1): on black they are filled red with a paper letter (5.4:1).
   - Links in the footer, text links and small buttons were 21 to 32px tall on touch screens: all are 44px now.
   - The day labels, file names, photo captions and staff labels were 13px: now 14px.
   - Tablets showed two very large flash designs a row: now three. The tablet footer puts the brand on its own row above three columns.
   - Headings no longer skip a level (footer titles are h2, the flash grid has a hidden h2), and the developer note with test booking codes is gone from Manage a booking.

Where Night ink and the first brief disagree, Night ink wins: the page background is black, not paper.

## Colour

| Token | Value | Use |
|---|---|---|
| sumi | `#080707` | page background |
| paper | `#F5E4D2` | text on black, flash sheets, scrolls, slips |
| paper-light | `#FBF2E8` | tiles, panels and inputs on paper |
| paper-on-sumi-soft | `rgba(245, 228, 210, .74)` | secondary text on black (8.9:1) |
| rule-night | `rgba(245, 228, 210, .22)` | thin lines on black |
| ink | `#201E1A` | text and outlines on paper |
| ink-soft | `#4A453D` | secondary text on paper |
| mist | `#8C877C` | lines and borders on paper, never text |
| red | `#B92423` | primary buttons, the seal, stamps, errors |
| wood | `#5A4330` to `#22190F` | scroll rods and the noren rod |

Red has one job: actions, the seal and stamps. Never use red for text on black (3.2:1 is too low); a paper label with a red bar beside it works instead, and an artist seal on black is filled red with a paper letter (5.4:1). Input borders on black use paper at 50% opacity (4.5:1), above the 3:1 that borders need.

## Breakpoints

| Width | Name | What changes |
|---|---|---|
| 1024px and up | Desktop | Header links, flash in four columns, staff sidebar |
| 768 to 1023px | Tablet | Menu button instead of the header links, flash in three columns, footer brand on its own row |
| up to 767px | Phone | One column, flash as rows with the picture on the left, bottom-sheet dialogs, staff top bar and the calendar as a list of days |

## Type

- Headings: Zen Old Mincho 600 and 900. Body and interface: Zen Kaku Gothic New 400, 500 and 700.
- Body 17px on desktop and 16px on phones, line-height 1.5. Nothing smaller than 14px. (The design CSS says 1.65, but the design pages loaded Tailwind's older reset after it, which put the body back to 1.5; every screen was drawn and approved at 1.5, so the website uses 1.5.)
- h1 56px / 38px on phones, h2 36px / 28px, h3 24px / 20px.
- Prices, times and booking codes use tabular numbers.
- Sentence case everywhere. No Japanese characters, no labels above headings, no arrows added to button text.

## Rules

- Square corners and no drop shadows. The only round shapes are the scroll-rod ends and the nail a scroll hangs from.
- Nothing is written on top of the paintings.
- Every button, link and field is at least 44px tall.
- Keyboard focus: a 2px paper outline on black, a 2px ink outline on paper, 3px offset.
- Text contrast: paper on black 16.2:1, soft paper on black 8.9:1, ink on paper-light 15:1, ink-soft on paper-light 8.6:1 (7.7:1 on paper).

## Building blocks

- **Header:** the BB seal and wordmark, links, and a red Book button. On tablets and phones the links move into a menu (large links, a strip of the night painting, opening hours and a call button).
- **Page banner:** a 150px strip of the night painting (92px on phones) that fades into black, then the page title.
- **Flash sheet:** a paper sheet with washi tape at the corners and a double ink border. Desktop shows a grid of tiles; phones show one design per row with the picture on the left.
- **Hanging scroll:** an artist's name written vertically, a signature piece and the artist's seal. Artists never have portrait photos.
- **Slip:** a paper panel with a double border for forms, booking summaries and dialogs.
- **Stamps:** red rotated stamps for customer statuses ("Deposit paid"), flat outlined labels in the staff tables.
- **Booking steps:** a numbered step bar (only the numbers and the current step's name on phones) and a slim footer with the phone number, so nothing pulls people away mid-booking.
- **Dialogs:** a paper slip over dimmed black; on phones it slides up from the bottom with full-width buttons.
- **Staff area:** the same colours with no paintings. Sidebar on desktop, a top bar with tabs on phones; the calendar becomes a list of days on phones and details open as full-height panels.

## Images

| Image | Where it's used | Original |
|---|---|---|
| Night torii painting | home hero, page banners, phone menu | `assets-incoming/footer-night.png` |
| Day torii painting | footer | `assets-incoming/hero-landscape.png` |
| BB seal | logo, favicon source, confirmation stamp | `assets-incoming/seal-bb.png` |

Web-sized copies are in `design-export-blade-and-brush/public/`. Flash art, artist work and the artists' signature pieces are empty placeholders until real images arrive (phase 3 stores them on Cloudinary).

## Screens

Phone designs were made for the customer pages and for the staff pages artists use on the go. The admin pages are desktop only. The tablet versions of the same screens are in the Figma file only.

| Screen | Route | Desktop | Phone |
|---|---|---|---|
| Home | `/` | [open](https://p.superdesign.dev/draft/40f3bb32-dd7e-42c4-892a-239530547bed) | [open](https://p.superdesign.dev/draft/9e9fed6a-0010-4e9c-b0be-0769f13ad30f) |
| Phone menu (open) | `/ (menu)` | none | [open](https://p.superdesign.dev/draft/ae86995b-eedf-41d1-8769-02f84feabbe9) |
| Flash gallery | `/flash` | [open](https://p.superdesign.dev/draft/bbd4b1d7-ed6a-43f0-b185-334aa3e14673) | [open](https://p.superdesign.dev/draft/faa89ed8-dc9d-4b3f-a22f-d5cae4466c03) |
| Artists | `/artists` | [open](https://p.superdesign.dev/draft/f7703e7d-4d35-46b5-81dc-0cd7daf67991) | [open](https://p.superdesign.dev/draft/97064144-6445-4a6e-a66c-f0ffa40673d4) |
| Artist page (Kai) | `/artists/:name` | [open](https://p.superdesign.dev/draft/f9c98920-ed54-4252-be9d-d4a37b613643) | [open](https://p.superdesign.dev/draft/342f12ef-c506-4ed6-8e90-e27037ff3b95) |
| Aftercare | `/aftercare` | [open](https://p.superdesign.dev/draft/d7b124d6-7fc0-4519-a133-b7c272f1bee4) | [open](https://p.superdesign.dev/draft/917d0457-afeb-4a0e-840f-5321963bebab) |
| Book: choose | `/book/choose` | [open](https://p.superdesign.dev/draft/6d960f99-ff4e-4a51-8929-18d922f3b107) | [open](https://p.superdesign.dev/draft/aa128b9f-6c22-451c-9c02-0e2394f415a4) |
| Book: time | `/book/time` | [open](https://p.superdesign.dev/draft/05dea13a-629d-41ba-adbc-823404d83797) | [open](https://p.superdesign.dev/draft/aa23292c-81b8-49d7-ad0b-1a7cedcb1b10) |
| Book: details | `/book/details` | [open](https://p.superdesign.dev/draft/2389c70d-3580-47a6-8a3f-425d93d2fdf3) | [open](https://p.superdesign.dev/draft/4af2f650-3ac7-455a-b6f7-3c67fc1901ae) |
| Book: deposit | `/book/deposit` | [open](https://p.superdesign.dev/draft/ef757ee7-dc7a-4fea-9d96-2e516479c822) | [open](https://p.superdesign.dev/draft/3cecc202-eb02-45e6-a8fd-29639c1ec4e3) |
| Booking confirmed | `/booking/confirmed` | [open](https://p.superdesign.dev/draft/823debe0-dfef-424b-b89e-8c4d82e30a7a) | [open](https://p.superdesign.dev/draft/313e1436-1c85-40e1-8ac1-461a38bf4ffb) |
| Manage a booking | `/manage` | [open](https://p.superdesign.dev/draft/a889483f-e5bd-4c43-a159-f41ccc3aeec3) | [open](https://p.superdesign.dev/draft/71a1e6b5-c0a3-484c-88b4-38d082c3b016) |
| Cancel dialog (under 48 hours) | `/manage` | [open](https://p.superdesign.dev/draft/0eef3eb3-7a01-4fc4-9bf5-2d3d27a7b608) | [open](https://p.superdesign.dev/draft/1979f330-c1ec-4c3a-85bd-a897d4b8d104) |
| My account | `/account` | [open](https://p.superdesign.dev/draft/77ac35ae-9cb3-42b7-ac1b-23e44252d959) | [open](https://p.superdesign.dev/draft/2a76c20b-6976-4da2-a354-1d506369ec42) |
| Review dialog | `/account` | [open](https://p.superdesign.dev/draft/252ae94f-e7f4-4ff2-b7ce-710506466082) | [open](https://p.superdesign.dev/draft/816fe157-cdba-4efa-8832-2250d4099d31) |
| Delete account dialog | `/account` | [open](https://p.superdesign.dev/draft/e6d76f5b-5099-4743-8fa8-5705fcaf7f08) | [open](https://p.superdesign.dev/draft/bd836063-287e-45fc-ba15-5bc80f7fe6fa) |
| Sign in | `/account/sign-in` | [open](https://p.superdesign.dev/draft/788a4d52-2da0-4e53-942a-6b58cdd16563) | [open](https://p.superdesign.dev/draft/7afcb9a3-ebab-4d13-a7e2-c522906721bf) |
| Register | `/account/register` | [open](https://p.superdesign.dev/draft/379a0d1a-5fc7-4165-b6cc-be4d8fb5f99c) | [open](https://p.superdesign.dev/draft/26380da0-cbaa-45ee-89bd-0ab593524f2e) |
| Not found | `*` | [open](https://p.superdesign.dev/draft/40ea4d28-3e93-46f7-86e8-c3ce3e23b9bf) | [open](https://p.superdesign.dev/draft/0397fef2-47d4-48cb-aad9-537e471146b1) |
| Image credits | `/credits` | [open](https://p.superdesign.dev/draft/5d023386-4fd0-45af-ad5d-f281891f30aa) | [open](https://p.superdesign.dev/draft/fadddc7f-7a10-4e0d-a6a3-c8f4cb5caebf) |
| Staff sign-in | `/staff/sign-in` | [open](https://p.superdesign.dev/draft/eac8eec2-303d-4272-b431-4f2e6da339c2) | [open](https://p.superdesign.dev/draft/20ba4301-5b11-4543-8041-867bd88c4ee9) |
| My calendar | `/staff/calendar` | [open](https://p.superdesign.dev/draft/27f22742-9565-4598-92d9-db92037d00b9) | [open](https://p.superdesign.dev/draft/7e58dbe1-0680-4df4-9614-437718ccaa55) |
| Booking details panel | `/staff/calendar` | [open](https://p.superdesign.dev/draft/27f22742-9565-4598-92d9-db92037d00b9) | [open](https://p.superdesign.dev/draft/de62cb77-8711-40ad-bfca-b4d9255cac88) |
| Reserve time panel | `/staff/calendar` | [open](https://p.superdesign.dev/draft/684a0459-cb48-4f76-bc49-a7950190c208) | [open](https://p.superdesign.dev/draft/0dd4481b-c062-46b9-abee-5afb69502443) |
| My hours | `/staff/hours` | [open](https://p.superdesign.dev/draft/5df14fd0-4f8c-4377-bd4b-e2412fc6d911) | none |
| My flash and work | `/staff/flash` | [open](https://p.superdesign.dev/draft/4e1926e8-b176-447a-87ef-d2788153693e) | none |
| Admin: bookings | `/admin/bookings` | [open](https://p.superdesign.dev/draft/27136147-d6ea-4296-ad05-b171d4ee0e14) | none |
| Admin: flash catalogue | `/admin/flash` | [open](https://p.superdesign.dev/draft/4ef8ff9b-8e21-4805-9f0e-eb61276c33bb) | none |
| Admin: artists | `/admin/artists` | [open](https://p.superdesign.dev/draft/935074a7-e732-4b31-8499-415dad8af01f) | none |
| Admin: reviews | `/admin/reviews` | [open](https://p.superdesign.dev/draft/86a58230-1294-4524-80a4-713520b0417a) | none |
| Admin: messages | `/admin/messages` | [open](https://p.superdesign.dev/draft/58c7ea67-9bca-48b2-90bd-b87127bff1ec) | none |
| Admin: users | `/admin/users` | [open](https://p.superdesign.dev/draft/4444bf7f-dd33-47a0-bef8-9d6decc9afde) | none |

## Source files

The canvas holds every version of every screen. The hand-written HTML behind the drafts, and the script that builds and imports them, are in `design-export-blade-and-brush/.superdesign/tmp/` (git-ignored, on this computer only):

- `night-ink-v4.html`: the home page and the shared styles
- `build.cjs` with `pages-booking.cjs`, `pages-account.cjs` and `pages-staff.cjs`: every other page
- `phone.cjs`: the phone layouts

- `fixes.cjs`: the November 2026 review fixes and the tablet layout

`node build.cjs` rebuilds the desktop pages, `node build.cjs --phone` the phone pages; add `--import` to send them to the canvas. `node build.cjs --all` writes one responsive file per screen into `pages-all/`, which the Figma build uses at every width.

The Figma screens are built from those pages by the scripts in `figma-build/`:

- `screens.cjs`: the list of screens per width and which ones are dialogs or panels
- `spec.js`: renders a page in the browser and describes its layout: flex rows and columns become auto layout, grids become rows of equal columns, and each box keeps its padding, spacing and fill, hug or fixed size
- `builder.js`: runs inside Figma and builds the frames from that description, binds colours to the variables, applies the text styles, places Button, Artist seal, Stamp and Logo instances, and turns links and buttons into prototype connections
- `pack.cjs`, `jobs.cjs` and `changed.cjs`: pack the descriptions into the upload that the builder reads

**Figma file:**

- **Design system:** the colour variables (named like the CSS variables, e.g. `text/on-sumi-soft` is `var(--paper-on-sumi-soft)`), 14 text styles and the components: Button, Artist seal (outline on paper, filled on black), Stamp, Text field, Logo and Flash tile.
- **Desktop · 1440, Tablet · 834, Phone · 390:** every screen in auto layout, in sections (Browse, Booking, Account, Artist area, Admin). A screen can be made wider or narrower and its content reflows; text uses the text styles and colours use the variables. Dialogs and staff panels are separate screen-sized frames that open as overlays.
- **Prototype:** press Play on a page. Desktop starts at Customer site, Artist area or Admin; tablet and phone at Customer site or Artist area. Links, the menu button, booking steps, sign-in, dialogs and the calendar panels all work. In the prototype's options, *Fit width* makes the screens fill the window. The three artist pages all open Kai's page, which stands in for all of them.

## The website (phase 4)

The customer pages in `client/` are the design pages turned into React: `client/src/styles/night-ink.css` is the design's CSS as it is, and `site.css` adds what static pages didn't need (pictures, loading and error states, real checkboxes and file inputs, the photo viewer, the animations). Every page was compared with its design page at 1440, 834 and 390px by measuring the position and size of each block, and the differences were fixed; what is left differs only where the sample data differs (more or fewer designs, reviews or free times).

Fixes found that way:

- The body line height: see *Type*.
- The design pages each carried only their own CSS, but the website has one stylesheet, so the flash gallery's card rules also reached the home page's cards (title row spacing and heading size). `site.css` gives the home cards their own values back.
- Sign in and Create an account use the design's centred form, full-width button and tick-box password rules.
- The flash gallery's consultation band goes between whole rows (after two of four rows on desktop), so no row is left half empty.

Deliberate differences from the drawings:

- *My account* has an *Edit details* button, as drawn, which needed a new server endpoint (`PUT /api/account`). The delete-account note says bookings still work with their code, because deleting an account keeps the bookings and their photos (the drawing said the references are deleted).
- Deleting an account asks for the password (the server requires it). The review dialog says the first name is shown with the review.
- *Cancel this booking?* starts with the focus on *Keep booking*, so a stray Enter doesn't cancel.
- The credits page lists the images the site really uses.

**Animations** (both described in the first brief, not drawn):

- **Katana cut:** the first time in a browser session someone presses one of the two buttons in the home page hero, a sword stroke cuts the painting from the top-right to the bottom-left corner, the halves slide apart, twelve petals fall, and the next page opens after 0.7 seconds. After that the buttons go straight there, and so do Ctrl/Cmd-clicks and middle clicks. With reduced motion it is a 0.2 second fade.
- **Seal stamp:** on *You're booked* the studio seal stamps down onto the page and a little ink spreads around it. With reduced motion it is simply there.

**Sample data:** `npm run dev:mock` runs the website on sample data in the browser, so every page and flow can be tried without the server (see the README).

## The artist and admin area (phase 5)

The staff pages follow the staff designs: `client/src/styles/staff.css` is the design's staff CSS as it is (the same block in every staff page), and the pages are built from the drawn markup. Checked in sample-data mode at 1440, 834 and 390px, with no sideways scrolling at any width.

Built beyond the drawings, because the server can do it and staff need it:

- **Calendar:** Day and Week views work, with *Today* to jump back; a week shows the days the artist works plus any day with bookings. Blocks say when they need attention (waiting for a deposit, deposit overdue, over and waiting for Done or No-show). The details panel has the customer's phone and email as links, the idea and reference photos (opening in the photo viewer), and the actions: Done, No-show, Cancel, and *Reserve a session* from a consultation, which opens Reserve time with the customer already chosen.
- **Reserve time:** the customer search shows matching people from the artist's consultations and sessions as they type; times are on the 30-minute grid; overlaps are refused with the server's message.
- **My hours:** a day off can also be a day with different hours, with a note. After saving, bookings that now fall outside the hours are listed (they stay booked).
- **My flash and work:** an edit dialog for each design (with its picture and a delete that is refused while it is held or booked), captions for uploaded photos, and a *Signature piece* section for the picture on the artist's scroll.
- **Bookings (admin):** a status filter and a search next to the drawn filters, this week's numbers above the list, paging, and the same details panel with the admin's extra actions (cash deposit, editing the customer's details and the studio's notes, deleting for good).
- **Artists, Reviews, Messages, Users:** edit dialogs, logins, deleting with a confirmation, a filter for reviews waiting for a decision and messages still waiting, *Reply by email* or *Call* on each message, and *Add user* for new staff and customer logins.

Deliberate differences from the drawings:

- **Tablets use the phone's top bar** (the seal, the name, sign out, and the pages as scrolling tabs). The drawn tablet layout stacks the whole sidebar above the page, which put about 500px of navigation before any content.
- **Weekly hours keep one line per day on tablets** (two on phones). The drawing stacks the day-off box and both times under each day below 1024px, which made the week about three screens tall.
- **An artist's login shows the artist's name** in the top bar (Kai, not the username kai).
- **Reset password** sets a new password that the admin passes on, as the server does; the drawing's "Password reset sent" note implied an email the server doesn't send.
- Every confirmation that loses something starts with the safe choice focused, like the customer site.

## Still open

- The image credits list the paintings' author, source and licence as "To be confirmed" until the final images are chosen.
- Flash designs, artist work and signature pieces are still empty frames in Figma too.
- Every staff page has been tried against the real server: the admin pages, and the artist pages (calendar and Reserve time, hours and days off with the conflicts note, flash designs with pictures, portfolio photos). The signature piece upload was left out there, as it has no remove button; it is covered by the server tests and sample-data mode.
- In Figma the header and footer are frames in each screen, not components, and the faint wave pattern behind the footer is left out.
