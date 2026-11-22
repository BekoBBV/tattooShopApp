# Blade & Brush — Tattoo Studio Booking Website

A full-stack booking website for Blade & Brush, a fictional traditional Japanese tattoo studio in Sofia: artist portfolios, flash tattoos booked directly, custom pieces planned in a free consultation (idea, placement, size and reference photos) and then tattooed in sessions the artist reserves with a price and deposit, an 18+ check and a consent form, and a staff area for artists and the studio owner.

**Live site: [tattoo-studio-it2n.onrender.com](https://tattoo-studio-it2n.onrender.com)** (free hosting: after about 15 minutes without visitors it sleeps, so the first visit can take a minute).

> **Blade & Brush is a concept project, not a real studio.** The address, phone number, Instagram handles, artists, prices and reviews are made up; bookings are real in the app but no money moves, because deposits go through a built-in test payment page. The tattoo photos, the night painting and the flash drawings were found online as examples, and the artists' scroll pictures are AI-generated (see the site's [Image credits](https://tattoo-studio-it2n.onrender.com/credits) page).

**Status: finished and online.** The project started from the code of [Fade Street](https://github.com/BekoBBV/berbershopp_app), a barbershop booking site, rebuilt for a tattoo studio: the sign-in, roles, users & rights, lists with search / sorting / paging, security, tests, CI and hosting setup carried over. The design is in [docs/design.md](docs/design.md), the server's API in [docs/api.md](docs/api.md) and the database in [docs/database.md](docs/database.md).

## Plan

| Phase | What | Status |
|---|---|---|
| 1 | Setup: this skeleton | Done |
| 2 | Design: name, theme and every screen on desktop, tablet and phone, with a clickable Figma prototype ([docs/design.md](docs/design.md)) | Done |
| 3 | Server: artists and styles, portfolios, flash designs, consultations, sessions of any length, deposits, consent; images on Cloudinary | Done |
| 4 | Website: home, artist portfolios, flash gallery, both ways to book, My account | Done; tried with sample data and against the real server |
| 5 | Artist and admin area: calendar, reserving sessions, deposits, flash and portfolio uploads | Done; every staff page tried against the real server |
| 6 | Tests, docs, security pass, going live | Done; live on Render |

## Tech stack

| Part | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, React Router (`client/`) |
| Backend | Java Spring Boot 4.1 (Web MVC, Data MongoDB, Security + JWT, Validation, Mail, Scheduling) (`server/`) |
| Database | MongoDB Atlas |
| Images | The sample pictures ship with the website; uploads go to Cloudinary |
| API docs | Swagger UI (springdoc-openapi, local only), [docs/api.md](docs/api.md) and [docs/database.md](docs/database.md) |
| Tests & CI | JUnit + Spring Boot Test, Vitest, GitHub Actions |
| Hosting | Docker image on Render (`Dockerfile`, `render.yaml`) |

## Run it locally

**Quickest on Windows:** once `secrets.properties` is set up (step 2), double-click **`start.bat`**. It builds the site, starts the server as one app and opens http://localhost:8080 when it is ready; close the window to stop it. **`reset-demo.bat`** does the same with fresh demo data (it asks first, and only runs while the app is stopped).

### 1. Install
- **JDK 17 or newer** (developed with OpenJDK 27)
- **Node.js 20 or newer** (developed with Node 24)
- A free **MongoDB Atlas** cluster (or a local MongoDB)

### 2. Configure the server
Copy `server/src/main/resources/secrets.properties.example` to `secrets.properties` in the same folder and fill in:

```properties
# Atlas: Connect > Drivers > Java; add the database name "/tattoo_studio" before the "?"
spring.mongodb.uri=mongodb+srv://USER:PASSWORD@CLUSTER.mongodb.net/tattoo_studio?retryWrites=true&w=majority

# Password for the "admin" staff login (created on first start)
app.admin.password=choose-a-strong-password

# Secret that signs staff login tokens: any random text of 32+ characters
app.jwt.secret=change-me-to-a-long-random-string-of-32-plus-characters

# Optional demo logins for the three artists (kai, dario, nika) and a demo customer
# (mira@example.com) with this password
#app.demo.artist-password=choose-a-demo-password

# Optional: booking emails via a free Mailtrap "Email Testing" inbox (emails are skipped without this)
#spring.mail.host=sandbox.smtp.mailtrap.io
#spring.mail.port=2525
#spring.mail.username=...
#spring.mail.password=...
```

`secrets.properties` is git-ignored — never commit it. In Atlas, also allow your IP under *Network Access*.

### 3. Start both parts (two terminals)

```bash
cd server
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

```bash
cd client
npm install
npm run dev
```

Open **http://localhost:5173**. The API runs on http://localhost:8080 (Vite forwards `/api` to it), with interactive docs at **http://localhost:8080/swagger-ui.html**.

On the first start the server fills an empty database with the demo artists (Kai, Dario and Nika) and their hours (Tuesday to Sunday, 11:00 to 20:00), twelve flash designs with their drawings, thirteen photos of finished work, the pictures on the artists' scrolls and nine reviews, and creates the `admin` login (plus the demo artist logins if `app.demo.artist-password` is set). In Swagger, sign in with `POST /api/auth/login` and paste the token under *Authorize* to try the staff endpoints.

### Try the website without the server

```bash
cd client
npm run dev:mock
```

Open **http://localhost:5173**. The website then runs on sample data in the browser instead of calling the server (`src/mock/mockApi.ts`, switched on by `.env.mock`): three artists, their flash designs and reviews, customers with bookings, and two contact messages. Any password of 8 or more characters works:

- Customers sign in at *Account* as `mira@example.com`.
- Staff sign in at *Staff sign-in* as `admin` or as the artists `kai`, `dario` and `nika`.

Everything works on it: booking, paying, cancelling, reviews, account changes, and every staff and admin page (reserving sessions, hours and days off, flash designs and photos, the admin's lists). The deposit is paid at once without a payment page. The sample data lives in the browser tab, so it survives reloads and starts fresh in a new tab, and it is left out of `npm run build`.

### Sign in to the staff area

Staff sign in at **/staff/sign-in** (*Staff sign-in* at the bottom of every page):

- **Artists** get *My calendar* (their week or day, the details of each booking, Done / No-show, cancelling, and *Reserve time* for a session with a customer from their consultations), *My hours* (weekly hours, days off and special hours, the break after each client) and *My flash and work* (their designs, the on-sheet switch, portfolio photos, the signature piece on their scroll).
- **The admin** gets *Bookings* (every artist's bookings with filters, this week's numbers, cash deposits, editing customer details), *Flash catalogue*, *Artists* (profiles, rates, logins, switching artists off), *Reviews* (what shows on the site), *Messages* (the contact form) and *Users* (blocking, new passwords, staff logins).

These logins work against the real server:

| Who | Username | Password |
|---|---|---|
| Admin (owner) | `admin` | `app.admin.password` from `secrets.properties` |
| Artists (demo) | `kai`, `dario`, `nika` | `app.demo.artist-password` from `secrets.properties` |

The admin can also create or reset an artist's login under *Artists*.

Customers sign in at **/account/sign-in** (*Account* in the menu) with their email, or create an account at /account/register. With demo logins turned on there is also a demo customer, `mira@example.com`, with the same demo password.

### Pictures and payments

The sample pictures (flash drawings, tattoo photos, the artists' scroll pictures, the night painting) ship with the website in `client/public/images`, each with a small WebP copy for lists. Uploaded pictures (flash drawings, portfolio photos, reference photos, message photos) are kept on Cloudinary when `app.images.cloudinary-url` is set in `secrets.properties` (or `CLOUDINARY_URL` in production), otherwise in `server/uploads/`.

Deposits are paid on the server's own test payment page: a button confirms the payment and no money moves. A real payment provider would plug in at `payment/PaymentProvider.java` (Stripe's test mode is the easiest to sign up for; Revolut's sandbox needs a verified business).

### Or run it as one app

Build the site once, and Spring Boot serves it together with the API on one address:

```bash
cd client
npm run build          # creates client/dist
cd ../server
./mvnw spring-boot:run # Windows: mvnw.cmd spring-boot:run
```

Open **http://localhost:8080**. Maven copies `client/dist` into the server on every build (`./mvnw package` makes one runnable jar: `java -jar target/server-0.0.1-SNAPSHOT.jar`; the jar never contains `secrets.properties`, so put that file next to the jar or use the environment variables from *Production mode*). Set `app.shop.website-url=http://localhost:8080` so the links in emails point there too. Without a client build the server simply runs as the API.

### Production mode

Start with the `prod` profile (`SPRING_PROFILES_ACTIVE=prod`) to turn off Swagger and the demo logins. Settings then come from environment variables instead of `secrets.properties`: `MONGODB_URI`, `APP_JWT_SECRET` (required, 32+ characters), `APP_ADMIN_PASSWORD`, `APP_SHOP_WEBSITE_URL` and optionally `SPRING_MAIL_*`. A password reset by the admin signs that person out everywhere at once. See `server/src/main/resources/application-prod.properties`. Behind a hosting proxy, tell the server which proxy to trust and which header carries the visitor's address (`server.tomcat.remoteip.internal-proxies` / `remote-ip-header`, see `application.properties` and `render.yaml`), otherwise every visitor looks like the proxy and they all share one set of limits.

### Put it online (Render)

The site runs at **https://tattoo-studio-it2n.onrender.com**. The `Dockerfile` builds the website and the server into one image, and `render.yaml` describes the service; every push to `main` deploys again. To set it up again:

1. **MongoDB Atlas:** under *Network Access*, allow `0.0.0.0/0` (Render's free plan has no fixed address). Take the connection string from *Connect > Drivers* and put the database name `tattoo_studio_live` before the `?` (without a name MongoDB quietly uses `test`).
2. **Render:** *New > Blueprint*, pick this repository, and fill in `MONGODB_URI`, `APP_ADMIN_PASSWORD` (the online `admin` login) and `APP_SHOP_WEBSITE_URL` (the address Render gives). The token secret is generated by Render.
3. **Cloudinary:** add `CLOUDINARY_URL` under the service's *Environment*. Render's disk is wiped every time the free service sleeps or deploys, and uploaded pictures with it.
4. **Logins:** production creates no demo logins. Sign in as `admin` and create the artists' logins under *Artists*; customers sign up themselves.

Render runs behind Cloudflare, so `render.yaml` makes the server read each visitor's address from Cloudflare's `CF-Connecting-IP` header, which visitors can't fake. The free plan sleeps after about 15 minutes without visitors, so the first visit then takes about a minute.

### Reset the demo data

Before a demo, start the server once with `--reset-demo`. It deletes **all** bookings, messages, reviews, logins, flash designs, portfolios and artists (with their uploaded pictures), then fills the database again with the original demo data, the `admin` login and (if configured) the demo artist logins:

```bash
cd server
./mvnw spring-boot:run -Dspring-boot.run.arguments=--reset-demo
```

On Windows: `mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--reset-demo"`. The server keeps running afterwards; next time start it normally.

## Tests

```bash
cd server
./mvnw test
```

Stop a running `spring-boot:run` first: the test build replaces the class files, and the dev server's automatic restart can catch them half-built (it then answers every request with 401 until restarted).

191 server tests and 50 website tests, all passing (the server tests against MongoDB Atlas). Sixteen of the website tests run the real pages against the sample data (`client/src/test/pages.test.tsx`): booking a flash design from the flash sheet to the deposit page, the design picker, a consultation, finding and cancelling a booking, sign-in sending customers, artists and the admin to their own area, customers signing out, the privacy policy and booking terms, the Instagram links and house-rule questions, the home page's Recent work, and booking an artist from the Artists page. The server tests cover availability on the 30-minute grid with breaks after each client, double booking and one booking per flash design (both enforced by database indexes, also for simultaneous bookings), the 15-minute deposit hold and what happens to late payments, deposits refunded or kept on cancelling, the 18+ check, consultations with reference photos (stored, served, deleted again when a booking fails or is wiped), sessions artists reserve for their own customers, the booking window, phone formats, the per-phone and per-account limits, lookup throttling, emails and reminders, the privacy clean-up jobs, staff login and roles (token expiry, lockout, artists limited to their own bookings and designs), customer accounts (sign-up, one account per email, changing your details, a new email only with the current password (wrong current passwords count with wrong sign-ins), passwords over BCrypt's 72-byte limit, My account, deposits, reviews, cancelling only your own bookings, sign-up limit), paging / sorting / search on every list, users & rights, flash designs, portfolios and signature pieces (set and removed by the artist or the admin; a photo needs a caption), the studio's newest work across all artists, the website's field limits matching the server's (`LimitsSyncTest`), status changes, working hours, the admin pages (Instagram handles checked and tidied, deposits paid in cash, deactivation guard, logins staying blocked after a reset, the artist picker list), stats and moderation, the security headers (content security policy, referrer and permissions policy), the API being closed by default (an `/api` address on no list is refused, even for the admin), the server log naming people without their email, and robots.txt and the sitemap. Integration tests use a separate `tattoo_studio_test` database on the same cluster, so they need the same `secrets.properties`, and they run with a fixed clock.

The website has unit tests for the form checks (the same rules as the server's):

```bash
cd client
npm test
```

GitHub Actions runs both on every push (`.github/workflows/ci.yml`), the server tests against a temporary MongoDB. Dependabot (`.github/dependabot.yml`) checks the website's and the server's libraries every week and opens a pull request when one has a fix or a security update.

## Project structure

```
client/src/             React app: pages/ (the customer site), pages/staff/ and pages/admin/ (the artist and
                        admin area, loaded only when opened), components/ (header and footer, flash cards,
                        scrolls, dialogs, the katana cut, the staff layout and booking panel), lib/ (api.ts,
                        the only place that calls the server; staff.ts; the booking draft; form checks),
                        styles/ (the design's Night ink and staff CSS, plus site.css),
                        mock/ (the sample data for npm run dev:mock)
client/public/images/   the pictures that ship with the site (flash, work photos, artists' scrolls, paintings)
server/
  config/               app setup, booking rules, demo data seeder, security + login tokens, Mongo settings
  controller/           REST endpoints: public (/api/...), artist (/api/staff/...), admin (/api/admin/...)
  service/              booking logic: availability, bookings, emails, privacy jobs, staff login, schedules, stats
  model/ repository/    MongoDB documents and data access
docs/design.md          the design: colours, type, rules, screens, and every change made after it
docs/api.md             the API reference (every endpoint, type and error code)
docs/database.md        the collections, their fields and rules
docs/figma-make-prompt… the first design brief (kept as a record; the site has moved on since)
start.bat               Windows: build the site, start the app, open the browser
reset-demo.bat          Windows: the same with fresh demo data
Dockerfile, render.yaml hosting: one image with site + server, and the Render setup
```

## Credits
- Pictures: listed on the site's Image credits page (`/credits`)
- Fonts: Zen Old Mincho and Zen Kaku Gothic New (SIL Open Font License), bundled via Fontsource
- Icons: Lucide (ISC License)
- Built on the code of Fade Street (same author)
