# Blade & Brush — Tattoo Studio Booking Website

A full-stack booking website for Blade & Brush, a fictional traditional Japanese tattoo studio in Sofia: artist portfolios, flash tattoos booked directly, custom pieces planned in a free consultation (idea, placement, size and reference photos) and then tattooed in sessions the artist reserves with a price and deposit, an 18+ check and a consent form, and a staff area for artists and the studio owner.

> **Status: design done, server written, customer website and staff area built; tests, security pass and going live are next.** This project starts from the code of [Fade Street](https://github.com/BekoBBV/berbershopp_app), a barbershop booking site, rebuilt for a tattoo studio. The sign-in, roles, users & rights, lists with search / sorting / paging, security, tests, CI and hosting setup carry over. The name, look and every screen are decided ([docs/design.md](docs/design.md)), the server has the tattoo studio's features ([docs/api.md](docs/api.md)), and the customer pages and the artist and admin area in `client/` follow the design at desktop, tablet and phone widths.

## Plan

| Phase | What | Status |
|---|---|---|
| 1 | Setup: this skeleton | Done |
| 2 | Design: name, theme and every screen on desktop, tablet and phone, with a clickable Figma prototype ([docs/design.md](docs/design.md)) | Done |
| 3 | Server: artists and styles, portfolios, flash designs, consultations, sessions of any length, deposits, consent; images on Cloudinary | Done |
| 4 | Website: home, artist portfolios, flash gallery, both ways to book, My account | Done; tried with sample data, not yet against the real server |
| 5 | Artist and admin area: calendar, reserving sessions, deposits, flash and portfolio uploads | Done; every staff page tried against the real server |
| 6 | Tests, docs, security pass, going live | Next |

## Tech stack

| Part | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, React Router (`client/`) |
| Backend | Java Spring Boot 4.1 (Web MVC, Data MongoDB, Security + JWT, Validation, Mail, Scheduling) (`server/`) |
| Database | MongoDB Atlas |
| Images | Cloudinary (from phase 3) |
| API docs | Swagger UI (springdoc-openapi), [docs/api.md](docs/api.md) and [docs/database.md](docs/database.md) (inherited, rewritten in phase 3) |
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

On the first start the server fills an empty database with the demo artists (Kai, Dario and Nika), their hours, twelve flash designs and a few reviews, and creates the `admin` login (plus the demo artist logins if `app.demo.artist-password` is set). In Swagger, sign in with `POST /api/auth/login` and paste the token under *Authorize* to try the staff endpoints.

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

Uploaded pictures (flash drawings, portfolio photos, reference photos, message photos) are kept on Cloudinary when `app.images.cloudinary-url` is set in `secrets.properties` (or `CLOUDINARY_URL` in production), otherwise in `server/uploads/`. Deposits are paid on a test payment page for now: a button confirms the payment and no money moves. The real Revolut payment page replaces it later (`payment/PaymentProvider.java`).

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

The `Dockerfile` builds the website and the server into one image, and `render.yaml` describes the service. On [Render](https://render.com): *New > Blueprint*, pick this repository, then fill in `MONGODB_URI`, `APP_ADMIN_PASSWORD` and `APP_SHOP_WEBSITE_URL` (the address Render gives, e.g. `https://tattoo-studio.onrender.com`). The token secret is generated by Render. Every push to `main` deploys again. Use the database name `tattoo_studio_live` in `MONGODB_URI` (without a name MongoDB quietly uses `test`), ideally with its own Atlas database user that can only read and write that database. In MongoDB Atlas, *Network Access* must allow Render's servers (Render shows them under *Connect > Outbound*). Render runs behind Cloudflare, so `render.yaml` makes the server read each visitor's address from Cloudflare's `CF-Connecting-IP` header, which visitors can't fake. The free plan sleeps after about 15 minutes without visitors, so the first visit then takes about a minute.

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

180 server tests and 22 website tests, all passing (the server tests against MongoDB Atlas). They cover availability on the 30-minute grid with breaks after each client, double booking and one booking per flash design (both enforced by database indexes, also for simultaneous bookings), the 15-minute deposit hold and what happens to late payments, deposits refunded or kept on cancelling, the 18+ check, consultations with reference photos (stored, served, deleted again when a booking fails or is wiped), sessions artists reserve for their own customers, the booking window, phone formats, the per-phone and per-account limits, lookup throttling, emails and reminders, the privacy clean-up jobs, staff login and roles (token expiry, lockout, artists limited to their own bookings and designs), customer accounts (sign-up, one account per email, changing your details and email, passwords over BCrypt's 72-byte limit, My account, deposits, reviews, cancelling only your own bookings, sign-up limit), paging / sorting / search on every list, users & rights, flash designs, portfolios and signature pieces, the website's field limits matching the server's (`LimitsSyncTest`), status changes, working hours, the admin pages (deposits paid in cash, deactivation guard, logins staying blocked after a reset, the artist picker list), stats and moderation, and the security headers (content security policy, referrer and permissions policy). Integration tests use a separate `tattoo_studio_test` database on the same cluster, so they need the same `secrets.properties`, and they run with a fixed clock.

The website has unit tests for the form checks (the same rules as the server's):

```bash
cd client
npm test
```

GitHub Actions runs both on every push (`.github/workflows/ci.yml`), the server tests against a temporary MongoDB.

## Project structure

```
client/src/             React app: pages/ (the customer site), pages/staff/ and pages/admin/ (the artist and
                        admin area, loaded only when opened), components/ (header and footer, flash cards,
                        scrolls, dialogs, the katana cut, the staff layout and booking panel), lib/ (api.ts,
                        the only place that calls the server; staff.ts; the booking draft; form checks),
                        styles/ (the design's Night ink and staff CSS, plus site.css),
                        mock/ (the sample data for npm run dev:mock)
server/
  config/               app setup, booking rules, demo data seeder, security + login tokens, Mongo settings
  controller/           REST endpoints: public (/api/...), artist (/api/staff/...), admin (/api/admin/...)
  service/              booking logic: availability, bookings, emails, privacy jobs, staff login, schedules, stats
  model/ repository/    MongoDB documents and data access
docs/api.md             the API reference (every endpoint, type and error code)
docs/database.md        the collections, their fields and rules
start.bat               Windows: build the site, start the app, open the browser
reset-demo.bat          Windows: the same with fresh demo data
Dockerfile, render.yaml hosting: one image with site + server, and the Render setup
```

## Credits
- Fonts: Zen Old Mincho and Zen Kaku Gothic New (SIL Open Font License), bundled via Fontsource
- Icons: Lucide (ISC License)
- Built on the code of Fade Street (same author)
