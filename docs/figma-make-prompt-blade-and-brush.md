# Figma Make prompts: Blade & Brush

Paste **Prompt 1** into a new Figma Make file first. When the result looks right, send the next prompt in the same chat, one at a time, in order. Small prompts give Figma Make fewer things to get wrong, and you can correct each part before moving on.

At the end there's a checklist to go through before exporting.

---

## Prompt 1: design system, layout and home page

```
Design a responsive website (mobile 390px first, then desktop 1440px) for "Blade & Brush", a fictional traditional Japanese tattoo studio in Sofia, Bulgaria. All text is in English. Build it in React + TypeScript with Tailwind CSS, React Router and lucide-react icons. Do not add any other UI library.

THE IDEA
The site looks like a flat graphic Japanese ink print: sumi ink, grey mist and vermilion on light washi paper. The home page is an ink landscape with a red torii gate. Pressing a booking button triggers one katana cut that slices the scene open. Everything else is calm, so the tattoo artwork is the hero.

STRICT RULES
- NO Japanese characters anywhere (no kanji, no kana, no fake Asian or "chop suey" fonts). The theme comes only from the visuals.
- Square corners everywhere (border-radius 0). No drop shadows. No gradients in the interface (soft mist in the illustrations is fine).
- No ALL-CAPS labels, no small letter-spaced labels above headings, no arrows appended to button text, no "A · B · C" strings joined with middle dots.
- Sentence case for all headings and buttons.
- Nothing moves on page load. The only animations are the katana cut (home page) and the seal stamp (booking confirmation).

COLOR TOKENS (define as CSS variables and use nothing else)
- paper #ECE6D8: page background
- paper-light #F5F1E8: inputs, panels, cards
- ink #201E1A: text, outlines, ink illustration
- ink-soft #5A554B: secondary text
- mist #8C877C: thin lines, borders, distant mountains (never body text)
- vermilion #C8361F: primary buttons, links on hover, the torii gate, the seal, maple leaves
- pine #2E3A33: deep forest shadows, rarely
- blossom #E6A1AE and blossom-deep #D2506A: ONLY in illustrations (cherry blossoms), never in the interface
Add a very faint paper grain texture over the paper background (about 4% opacity noise).

TYPOGRAPHY
- Headings: "Zen Old Mincho" (Google Fonts), weights 600 and 900.
- Body and interface: "Zen Kaku Gothic New", weights 400, 500 and 700.
- Body 16px, line-height 1.65, lines no longer than about 70 characters.
- Headings: h1 34px mobile / 56px desktop, h2 26px / 36px, h3 20px / 24px, line-height 1.2.
- Prices and durations use tabular numbers.

COMPONENTS
- Primary button: solid vermilion, paper-colored text, Zen Kaku Gothic New 500, 48px tall, square corners.
- Secondary button: transparent with a 1.5px ink border, ink text.
- Links: ink, underlined; vermilion on hover.
- Inputs: paper-light background, 1px mist border, ink border on focus, label above the field, helper or error text below. Errors in vermilion with a short plain explanation of how to fix it.
- Keyboard focus: a visible 2px ink outline with 3px offset on everything interactive.
- Icons: lucide-react, thin (stroke 1.5).

LOGO: THE SEAL
A square red seal stamp, like a Japanese artist's hanko: vermilion square with the letters "B B" carved out in paper color, in a bold serif, slightly uneven edges as if pressed by hand, rotated about -3 degrees. Next to it the wordmark "Blade & Brush" in Zen Old Mincho 900. Make it a reusable component in sizes 32, 48 and 96px.
Each artist has a smaller seal of the same style with one initial: K, D, N.

HEADER
- Mobile: seal logo on the left, a vermilion "Book" button and a menu button on the right. The menu opens a full-screen paper overlay with large Zen Old Mincho links (Flash, Artists, Aftercare, Manage a booking, Account) and a close button.
- Desktop: seal and wordmark on the left; links Flash, Artists, Aftercare, Account in the middle or right; "Book" button always visible on the far right.
- The header sits on paper with a 1px mist line under it.

FOOTER
A thin ink line drawing of a pine branch across the top of the footer, then: the seal, "Blade & Brush, traditional Japanese tattooing in Sofia", a placeholder address in central Sofia, opening hours (Tuesday to Sunday, 11:00 to 23:00; closed on Monday), a placeholder phone number +359 88 000 0000, links (Manage a booking, Aftercare, Staff sign-in, Image credits).

HOME PAGE: THE SCENE
A full-screen illustration in a FLAT GRAPHIC INK style (clean shapes, like a modern screen-printed poster of a Japanese landscape; limited palette of ink, greys, vermilion and blossom pink; no photographic textures). Build it as separate SVG layers so it can be animated:
1. Paper sky.
2. A distant mountain in thin mist-grey outline with pale fill.
3. Horizontal bands of soft mist.
4. Middle-distance ink pines and rocks.
5. A large vermilion torii gate in the center, with a black top beam that curves up at both ends. It frames the page title.
6. Tall dark ink pines in the foreground on the left and right edges.
7. A cherry blossom branch overhanging from the top-right corner: dark ink branch, flat pink blossom clusters, a few deeper pink flowers.
8. At the bottom: grey rocks, a small river with white water, and bright red maple trees near the river.
Portrait composition on mobile (tall scene, gate in the middle), wider landscape composition on desktop (same elements, more space on the sides).

Inside the gate, between the pillars:
- h1 "Blade & Brush"
- one line: "Japanese tattooing in Sofia. Flash designs you can book today, custom pieces planned with your artist."
- primary button "Book a flash design" and secondary button "Plan a custom piece"

HOME PAGE: THE KATANA CUT
When the visitor presses either of the two hero buttons for the first time in a browser session:
1. 0 to 150ms: a thin bright line (paper-white core with a faint vermilion edge) is drawn diagonally across the whole scene, from the top-right corner to the bottom-left, like a sword stroke.
2. 150 to 700ms: the scene splits along that line into two halves. The upper-left half slides up and to the left, the lower-right half slides down and to the right, both fading out, revealing the destination page behind them.
3. At the same moment, 10 to 14 cherry petals burst from points along the cut line and drift down, fading out by 1200ms.
4. Then the app navigates to the destination (flash gallery or consultation booking).
After the first cut, the buttons navigate instantly for the rest of the session.
If the device prefers reduced motion: a simple 200ms cross-fade, no petals.
Nothing in the scene moves before the click.

HOME PAGE: BELOW THE SCENE
1. "Flash you can book today": 6 flash designs on a paper "flash sheet" (thin ink frames, each design with its name, the artist's small seal, size, time and price). Link "See all flash designs".
2. "Three artists, three styles": the three artists as small hanging scrolls (see Prompt 2 for the scroll style), linking to their pages.
3. "How booking works", a real sequence, so numbered: 1 Choose a flash design. 2 Pick a time that suits you. 3 Pay a €30 deposit with Revolut. The rest you pay in the studio.
4. "Want something of your own?": short text "Bigger and custom pieces start with a free 30-minute consultation. Bring your idea and reference photos, and your artist will plan the piece and the sessions with you." Button "Book a free consultation".
5. "Write to the studio": a contact form (name, email or phone, message, attach up to 5 photos) with the button "Send message".
6. Footer.
```

---

## Prompt 2: page banners, flash gallery, artists and aftercare

```
Continue the same design system. Add these pages.

PAGE BANNERS
Every page except the home page starts with a narrow ink banner (88px tall on mobile, 140px on desktop) in the same flat graphic ink style as the home scene, with the page title (h1) under it on the left. Each page has its own motif:
- Flash gallery: stylized waves.
- Artists and artist pages: pine branches.
- Booking steps: mountain and mist.
- Aftercare: maple leaves and a few cherry blossoms.
- Account, sign-in, register, manage booking: river stones.
- Staff and admin pages: NO banner.

FLASH GALLERY (/flash)
- Intro line: "Ready-made designs at a fixed price. Pick one, choose a time, and it's booked."
- Filters as simple toggle buttons: artist (All, Kai, Dario, Nika), size (Small, Medium, Large).
- A grid like a flash sheet pinned on a studio wall: 2 columns on mobile, 4 on desktop. Each design on a paper-light tile with a thin ink frame: the image, design name (Zen Old Mincho), the artist's seal, "8 × 12 cm", "About 2 hours", price "€180", and a "Book" button.
- Halfway down the grid, a full-width band: "Want something of your own? Book a free consultation to plan your tattoo." with the button "Book a free consultation".
- Empty state for filters with no results: "No flash designs match these filters. Clear the filters or book a consultation for a custom piece."

SAMPLE FLASH DATA (12 designs; use simple ink placeholder drawings, real images come later)
Kai (bold traditional colour): Lucky koi, 6 × 8 cm, 1.5 h, €140 | Koi and wave, 8 × 12 cm, 2.5 h, €220 | Small dragon, 6 × 12 cm, 2 h, €180 | Tiger head, 10 × 10 cm, 3 h, €260
Dario (black and grey): Hannya mask, 8 × 10 cm, 2.5 h, €200 | Oni, 7 × 9 cm, 2 h, €170 | Kabuto helmet, 8 × 8 cm, 2 h, €170 | Katana and smoke, 4 × 16 cm, 1.5 h, €130
Nika (fine line): Crane in flight, 6 × 8 cm, 1 h, €90 | Peony, 6 × 6 cm, 1 h, €90 | Cherry blossom branch, 4 × 14 cm, 1.5 h, €110 | Small wave, 5 × 5 cm, 1 h, €80

ARTISTS (/artists)
Three hanging scrolls side by side on desktop, stacked on mobile. Each scroll:
- a dark wooden rod across the top and the bottom (pine color, slightly wider than the scroll, with rounded rod ends; the only rounded shapes on the site)
- a tall narrow paper body (about 1:2.6)
- the artist's name written vertically down the left side, upright letters stacked one under another, in Zen Old Mincho 900, large
- one of the artist's signature pieces filling the rest of the scroll
- the artist's small seal stamped near the bottom
- under the scroll: the style in one short line
NO portrait photos of the artists anywhere on the site.
Artists:
- Kai: "Bold traditional colour"
- Dario: "Black and grey, ink wash"
- Nika: "Fine line"
The whole scroll links to the artist's page.

ARTIST PAGE (/artists/:name), example Kai
- Top: a wide version of the scroll (vertical name on the left, a large signature piece, the seal).
- About: "Kai tattoos in the bold traditional Japanese style: heavy outlines, solid colour, and backgrounds of wind and water. Most of Kai's work is large: sleeves, backs and chest pieces."
- Rates: "Flash at fixed prices. Custom work €90 per hour, or €550 for a full day on large pieces."
- Buttons: "Book a flash design by Kai" and "Plan a custom piece with Kai".
- "Flash by Kai": the artist's flash designs (same tiles as the gallery).
- "Work": a gallery of portfolio photos (neutral placeholders for now), opening larger in a simple viewer with next/previous and close.
- "Reviews": average rating out of 5, then reviews with first name, date and text.
Dario: "Dario works only in black and grey: masks, demons and warriors, shaded like ink on paper." Rates €80 per hour, €500 full day.
Nika: "Nika works with the finest needles: flowers, cranes and waves in thin black lines with small touches of red. A good choice for a first tattoo." Rates €70 per hour, €420 full day.

AFTERCARE (/aftercare)
A vertical timeline (this is a real sequence, so mark each stage with its day range):
- Day 1: keep the wrap on for 2 to 4 hours, then wash gently with lukewarm water and unscented soap, pat dry, and apply a thin layer of the aftercare cream.
- Days 2 to 3: wash twice a day, thin layer of cream, wear loose clean clothes over it.
- Days 4 to 14: peeling and itching are normal. Don't scratch or pick. No swimming, sauna or sunbathing.
- Days 15 to 30: the skin settles. Keep it moisturized and out of strong sun.
- After a month: always use high SPF sunscreen on the tattoo to keep the colors sharp.
End with: "Something doesn't look right? Write to the studio or call us." and a button "Write to the studio".
```

---

## Prompt 3: booking, consultation, deposit and confirmation

```
Continue the same design system. Build the booking flow and booking management.

BOOKING STEPS (/book)
A step indicator at the top: 1 Choose, 2 Time, 3 Details, 4 Deposit. Steps are a real sequence, so numbers are fine. Show the current step clearly; finished steps can be clicked to go back.

Step 1, Choose (/book/choose)
Two options, side by side on desktop, stacked on mobile:
- "A flash design": if the visitor came from a flash tile, that design is already selected and shown (image, name, artist seal, size, time, price) with a link "Choose another design".
- "A free consultation": 30 minutes with the artist of your choice, to plan a bigger or custom piece. Choose the artist (Kai, Dario, Nika) with their seal and style line.
Button "Continue".

Step 2, Time (/book/time)
- A row of days (the next 14 days; Monday is closed and shown as disabled), then the free start times for the chosen day as a grid of time buttons.
- Explain what's shown: "Times when Kai is free for about 2 hours."
- Empty day: "Kai has no free 2-hour slot on this day. Try another day or another artist's design."
Button "Continue".

Step 3, Details (/book/details)
- Full name, phone, email.
- Date of birth. If under 18: error "You need to be 18 or older to get tattooed."
- Only for consultations: "Your idea" (text area), "Placement" (select: forearm, upper arm, full sleeve, shoulder, chest, back, ribs, thigh, calf, other), "Approximate size" (cm), "Colour" (Full colour / Black and grey / Not sure), and "Reference photos": drop zone or button "Add photos", up to 5 photos, JPG, PNG or HEIC, up to 10 MB each, showing thumbnails with a remove button.
- Consent, three checkboxes:
  "I am 18 or older."
  "I have read and agree to the consent form." (the consent form opens in an expandable section: health questions, allergies, no alcohol 24 hours before, the artist may refuse if unsafe)
  "I understand the deposit policy: the €30 deposit is taken off the price, refunded if I cancel at least 48 hours before, and kept if I cancel later."
  (Consultations need only the first two; they have no deposit.)
- A quiet note at the top for guests: "No account needed. Have an account? Sign in to fill this in faster."
Button "Continue" (flash) or "Book consultation" (consultation, which skips step 4).

Step 4, Deposit (/book/deposit)
- Summary: design, artist, date and time, price €180, deposit €30, "Pay in the studio: €150".
- A countdown: "We're holding this time for you for 14:32." (15 minutes). When it runs out: "The hold expired and the time is free again. Pick a time again to continue." with a button "Pick a time".
- Primary button "Pay €30 deposit with Revolut". Pressing it shows a short state "Taking you to Revolut to pay..." (the real Revolut payment page comes later).
- Under the button: "You can pay with Revolut Pay, a card, Apple Pay or Google Pay."

CONFIRMATION (/booking/confirmed)
- The B B seal stamps onto the page: it starts at 130% size and 0 opacity, rotated a little more, and presses down to its normal size and rotation in 350ms, with a faint ink spread around it. Reduced motion: it simply appears.
- h1 "You're booked"
- The booking code, large: "K7Q2-M9". Text: "Keep this code and your phone number. You need both to manage or cancel your booking."
- Details: what, artist, date and time, deposit paid, left to pay in the studio.
- "Before you come": eat a proper meal, bring an ID, wear clothes that leave the placement free, no alcohol the day before.
- Links: "Add to calendar", "Read the aftercare guide", "Back to home".
- For a consultation the page says "Your consultation is booked" and lists the photos that were sent.

MANAGE A BOOKING (/manage)
- Form: booking code and phone number. Button "Find booking". Error: "No booking matches this code and phone number. Check both and try again."
- Booking found: details and a status label: "Deposit paid", "Waiting for deposit", "Consultation booked", "Cancelled", or "Deposit refunded".
- If the booking is a session the artist reserved after a consultation and the deposit isn't paid yet: details (date, start and end time, description, price €480, deposit €100) and the button "Pay €100 deposit with Revolut".
- Button "Cancel booking" opens a confirmation dialog:
  48 hours or more before: "Cancel this booking? Your €30 deposit will be refunded to your card." Buttons "Cancel booking" and "Keep booking".
  Less than 48 hours before: "Cancel this booking? It's less than 48 hours away, so the €30 deposit can't be refunded." Same buttons.
  After cancelling: "Booking cancelled." plus the refund status if there is one.
```

---

## Prompt 4: customer account and other pages

```
Continue the same design system. Add the account pages and the not-found page. Banner motif: river stones.

SIGN IN (/account/sign-in): email, password, button "Sign in", links "Create an account" and "Manage a booking without an account".
REGISTER (/account/register): full name, email, phone, date of birth (18+ check), password with requirements shown, button "Create account".

MY ACCOUNT (/account)
- "Upcoming": cards for each booking, newest first, with type ("Flash", "Consultation", "Session reserved by Dario"), design or description, artist seal, date and time, status, and actions ("Pay €100 deposit", "Cancel booking").
  Example reserved session: "Back piece, session 1 of 3", Saturday 14 March, 11:00 to 15:00, price €480, deposit €100 waiting.
- "Consultations": each consultation with the idea text and thumbnails of the reference photos sent.
- "Past": past bookings, with a link "Leave a review" for finished flash and sessions.
- "Account details": name, phone, email, change password, sign out, delete account.
- Empty state: "No bookings yet. Book a flash design or a free consultation." with both buttons.

NOT FOUND (*): banner, h1 "This page doesn't exist", text "The link may be old or mistyped.", button "Back to home".

IMAGE CREDITS (/credits): a simple list of image credits (file, author, source, licence) that will be filled in later.
```

---

## Prompt 5: artist and admin area

```
Continue the same colors and fonts, but this is the staff area: plain and practical, NO illustrations or banners, compact spacing. Layout: a left sidebar on desktop (seal, staff name, links, sign out), a top bar with a menu on mobile. Tables become stacked cards on mobile.

STAFF SIGN-IN (/staff/sign-in): username and password, button "Sign in".

ARTIST AREA (signed in as Dario)
1. My calendar (/staff/calendar)
   - Day and week views; Tuesday to Sunday, 11:00 to 23:00.
   - Bookings as blocks: flash in ink, consultations as an outlined pine block, reserved sessions in vermilion, days off in a hatched mist pattern. Each block shows the time, customer first name and design or description.
   - Clicking a block opens details: customer name and phone, design or idea, placement, size, the consultation's reference photos (open larger), deposit status.
   - A button "Reserve time" opens a panel: customer (search by name or phone among this artist's consultation customers), date, start time, end time, description (e.g. "Back piece, session 1 of 3"), price €, deposit €. Button "Reserve time". Explain: "This time is removed from online booking and the customer sees the session in their bookings."
2. My hours (/staff/hours)
   - Weekly hours: for each weekday a start and end time, or "Day off".
   - Days off: a list of dates with a reason (e.g. "Tattoo convention, Milan"), button "Add day off".
   - "Break after each client": a number of minutes (default 30).
   - Button "Save hours", success message "Hours saved."
3. My flash and work (/staff/flash)
   - My flash designs: a list with image, name, size, time, price and an "Available" switch; button "Add flash design" opens a form (image upload, name, size in cm, time in hours, price €).
   - My work: a grid of portfolio photos with upload and remove.

ADMIN AREA (signed in as the studio owner)
1. Bookings (/admin/bookings): table with date, time, customer, artist, type, status and deposit status ("Paid with Revolut", "Paid in cash", "Waiting", "Refunded"). Filters by date, artist, type and deposit status. Row actions: "Mark deposit paid in cash", "Cancel booking". Bookings waiting for a deposit past their hold time are highlighted.
2. Flash catalogue (/admin/flash): all artists' designs, filter by artist, "Available" switch, edit.
3. Artists (/admin/artists): the three artists with their seal, style line, rates, "Active" switch, and their staff login; button "Add artist".
4. Reviews (/admin/reviews): list with rating, text, artist, date, "Show on site" switch.
5. Messages (/admin/messages): inbox of contact form messages with sender, date, text and attached photo thumbnails (open larger); "Mark as handled".
6. Users (/admin/users): customers and staff accounts with role (Customer, Artist, Admin), "Active" switch and "Reset password".
```

---

## Checklist before exporting

- [ ] No Japanese characters anywhere, and no "Asian-style" novelty fonts.
- [ ] Headings in Zen Old Mincho, body in Zen Kaku Gothic New.
- [ ] Square corners and no shadows on cards, buttons and inputs.
- [ ] Blossom pink appears only in the illustrations.
- [ ] The katana cut plays once per visit, shows petals, and is a simple fade with reduced motion.
- [ ] The seal stamp plays on the confirmation page.
- [ ] Every page except the home page and staff area has its own ink banner.
- [ ] Artists have no portrait photos: only the vertical name, their work and their seal.
- [ ] Buttons say exactly what happens ("Pay €30 deposit with Revolut", "Cancel booking").
- [ ] It works at 390px wide without sideways scrolling, and keyboard focus is visible everywhere.

When it's done, export the code into a folder named `design-export-blade-and-brush` inside the project. Git ignores it, and I'll take what the site needs from it.

---

## Prompt 6: fixes after the first review

**Before sending:** attach these four images to the Figma Make chat. They're in `TattooStudioApp/assets-incoming/figma-upload/`:
- `hero-portrait.jpg`
- `hero-landscape.jpg`
- `banner-sky.jpg`
- `seal-bb.png`

```
Fix the following across the whole site. Keep all pages, routes, flows, texts and the staff area layout exactly as they are unless a point below says otherwise.

1. NEW COLORS (update the CSS variables; the attached painting decides them)
- paper #F5E4D2 (page background), paper-light #FBF2E8 (inputs, panels, tiles)
- ink #201E1A, ink-soft #4A453D (secondary text; never lighter than this for text)
- mist #8C877C (lines and borders only)
- red #B92423 replaces vermilion everywhere (primary buttons, links on hover, active states, errors)
- remove the blossom pink tokens; the pink now lives only in the images

2. HOME HERO: USE THE PAINTING, NO TEXT ON IT
- Delete the drawn SVG scene and the translucent panel behind the hero text completely.
- Show the attached painting full width: <picture> with hero-portrait.jpg below 768px and hero-landscape.jpg from 768px. object-fit: cover, object-position: 50% 60% (keeps the gate in view).
- Image height: 60svh on mobile; on desktop calc(100svh - 64px - 230px), min 420px.
- Fade the bottom 15% of the image into the paper color with a CSS mask, so there is no hard edge.
- Directly under the image, on plain paper, centered: h1 "Blade & Brush", the line "Japanese tattooing in Sofia. Flash designs you can book today, custom pieces planned with your artist.", then the buttons "Book a flash design" (primary) and "Plan a custom piece" (secondary), side by side on desktop, full width and stacked on mobile. On a 390 x 844 phone the buttons must be visible without scrolling.
- Nothing is written on top of the painting.

3. THE KATANA CUT NOW CUTS THE PAINTING
Same timing as before, but applied to the image area: render the painting twice, clipped with clip-path polygons into the upper-left and lower-right halves along the diagonal from the top-right to the bottom-left corner. Draw the bright slash line over it, then slide the halves apart. The petals are small soft-pink (#E6A1AE) SVG petal shapes added only during the cut. Reduced motion: 200ms fade, no petals. The title and buttons below the image fade out with the cut.

4. PAGE BANNERS: USE THE PAINTING STRIP
Replace every drawn banner (waves, pines, mountains, leaves, stones) with the attached banner-sky.jpg on all customer pages: height 96px on mobile, 150px on desktop, object-fit: cover, object-position: 50% 45%, bottom 30% fading into the paper color with a CSS mask. The page title (h1) follows below it on paper. Staff and admin pages keep no banner.

5. THE REAL SEAL
- Use the attached seal-bb.png (transparent background) for the logo everywhere: header 44px, footer 64px, the confirmation stamp 120px. Delete the drawn seal component.
- The browser-tab favicon: a simple SVG, a square #B92423 frame (rounded corners 12%) with "BB" inside in Zen Old Mincho 900, also #B92423, transparent background.
- The artist seals K, D and N stay drawn for now, but restyle them to match: a red #B92423 frame with the single letter in red, transparent inside, no filled red square.

6. NO LABELS ABOVE HEADINGS
Remove every small colored label placed above a heading (for example "Book your tattoo" above "Choose what to book", and the style line above "About" on the artist page). Where the information is useful, show it as a normal line of body text below the heading instead (the artist's style goes directly under the artist's name).

7. READABLE TEXT
- Body text 17px on desktop and 16px on mobile, line-height 1.65, color ink #201E1A.
- Secondary text (helper text, meta info, captions) at least 14px in ink-soft #4A453D.
- Every text must reach a contrast ratio of at least 4.5:1 against its background.

8. FLASH AND PORTFOLIO PLACEHOLDERS
Remove all drawn doodles in flash tiles, artist scrolls and portfolio galleries. Use a plain paper-light tile with a 1px mist frame and nothing inside until real images are added. Keep the names, seals, sizes, times and prices around the tile.

9. CONFIRMATION PAGE
The seal stamp uses seal-bb.png and ends at 100% opacity (it must not stay faded). The booking code and all booking details are visible immediately, they do not wait for the animation, and there is no large empty gap between the seal, the heading and the details.

Finally, check every customer page at 390px and 1440px wide: no horizontal scrolling, no text on top of images, and every heading starts directly with the heading text (no label above it).
```

---

## Prompt 7: polish after the second review

No images to attach this time.

```
Polish pass. Keep all pages, routes, texts, images and colors exactly as they are, except for these points:

1. BOOKING STEP BAR ON PHONES
On mobile the connecting line runs through the step labels so they look crossed out, and step 4 is cut off. Fix it: on screens under 640px show only the four numbered squares connected by a line BETWEEN them (never behind text), with just the current step's label written below the bar, for example "Step 2 of 4: Time". All four steps must fit within 375px without scrolling. Desktop stays as it is, but make sure the line never touches the labels there either.

2. ARTISTS ON PHONES
Each hanging scroll is now full width and very tall on mobile. Under 640px, show the three scrolls in a horizontal row the visitor can swipe (CSS scroll-snap), each scroll 72% of the screen width, so the next scroll peeks in from the right. Keep the 1:2.6 proportion. Desktop stays three side by side.

3. CONFIRMATION SEAL
After the stamp animation, the seal must be fully opaque and fully saturated, exactly like the logo in the header. Right now it stays pale and pinkish. No opacity, filter or blend mode may remain on it once the animation ends.

4. MOBILE HEADER
Show the wordmark "Blade & Brush" (Zen Old Mincho 600, 18px) next to the seal on mobile too, as long as it fits next to the Book button and the menu button at 375px. If the screen is narrower than 360px, hide the wordmark.

5. ONE PAPER BACKGROUND
Use the paper color #F5E4D2 as the background of every section on customer pages. Use paper-light #FBF2E8 only for tiles, cards, inputs and panels, not for whole sections. Separate sections with spacing (96px desktop, 64px mobile) and, where needed, a single 1px mist line. No alternating background bands.

6. HORIZONTAL SCROLL AREAS
Where something scrolls sideways (the day picker in the booking time step, the artist scrolls on mobile), hide the browser scrollbar visually but keep scrolling by swipe, trackpad and keyboard. Let the last item peek in so it's clear there is more.

7. SAMPLE DATES
All sample bookings, calendar weeks and reviews use dates in late 2026 and early 2027 (the studio is new; nothing from 2025).

8. IMAGE-READY TILES
Prepare the flash tiles, artist scrolls and portfolio gallery for real images: flash tiles are square (1:1), the image inside uses object-fit: contain on paper-light with 12px padding (flash art is drawn on light paper); portfolio photos use object-fit: cover in a 4:5 frame. Until images exist, keep the empty tiles, but at the correct proportions.

9. CHECK
Check every customer page and the staff calendar at 360px, 375px, 768px and 1440px: nothing cut off, no horizontal page scrolling, no line crossing text, all buttons at least 44px tall, and keyboard focus visible on every link, button, input and day or time option.
```
