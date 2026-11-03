# Tattoo Studio — Booking Website (working title)

A full-stack booking website for a tattoo studio: artist portfolios, flash tattoos booked directly, custom tattoos by request (idea, placement, size and reference images, then a quote from the artist), deposits, an 18+ check and a consent form, and a staff area for artists and the studio owner.

> **Status: skeleton.** This project starts from the code of [Fade Street](https://github.com/BekoBBV/berbershopp_app), a barbershop booking site, renamed for a tattoo studio (barbers are now artists). The sign-in, roles, users & rights, lists with search / sorting / paging, security, tests, CI and hosting setup carry over. The studio's name, look and tattoo-specific features come next; some texts and demo data still read like a barbershop until then.

## Plan

| Phase | What |
|---|---|
| 1 | Setup: this skeleton |
| 2 | Design: name, theme and every screen in Figma (desktop + phone) |
| 3 | Server: artists and styles, portfolios, flash designs, requests and quotes, sessions of any length, deposits, consent; images on Cloudinary |
| 4 | Website: home, artist portfolios, flash gallery, both ways to book, My account |
| 5 | Artist and admin area: request inbox, quotes, calendar, deposits, portfolio uploads |
| 6 | Tests, docs, security pass, going live |

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
# Atlas: Connect > Drivers > Java; add the database name "/tattoo studio" before the "?"
spring.mongodb.uri=mongodb+srv://USER:PASSWORD@CLUSTER.mongodb.net/tattoo_studio?retryWrites=true&w=majority

# Password for the "admin" staff login (created on first start)
app.admin.password=choose-a-strong-password

# Secret that signs staff login tokens: any random text of 32+ characters
app.jwt.secret=change-me-to-a-long-random-string-of-32-plus-characters

# Optional demo logins for all six artists (jax, melo, sol, nia, cruz, rook) and a demo customer
# (alex@example.com) with this password
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

On the first start the server fills an empty database with the demo services, artists, hours and reviews, and creates the `admin` login (plus the demo artist logins if `app.demo.artist-password` is set). Staff sign in at **http://localhost:5173/staff/login**. In Swagger, sign in with `POST /api/auth/login` and paste the token under *Authorize* to try the staff endpoints.

### Sign in to the staff area

Go to **/staff/login** (or the *Staff* link in the menu):

| Who | Username | Password |
|---|---|---|
| Admin (owner) | `admin` | `app.admin.password` from `secrets.properties` |
| Artists (demo) | `jax`, `melo`, `sol`, `nia`, `cruz`, `rook` | `app.demo.artist-password` from `secrets.properties` |

The admin can also create or reset an artist's login under *Artists*.

Customers sign in at **/account/sign-in** (*Sign in* in the menu) with their email, or create an account at /account/register. With demo logins turned on there is also a demo customer, `alex@example.com`, with the same demo password.

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

Before a demo, start the server once with `--reset-demo`. It deletes **all** bookings, messages, reviews, logins, services and artists, then fills the database again with the original demo data, the `admin` login and (if configured) the demo artist logins:

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

150 server tests and 20 website tests, all green. The backend unit and integration tests cover availability, double booking (including the database index), the booking window, phone formats, the per-phone and per-account limits (also for simultaneous bookings), lookup throttling, the cancellation cutoff, emails and reminders, the privacy clean-up jobs, staff login and roles (token expiry, lockout, artists limited to their own bookings), customer accounts (sign-up, one account per email, My bookings, cancelling only your own bookings, sign-up limit), paging / sorting / search on every list (who may search, filters, sort order, bad parameters), users & rights (every kind of login, role and artist changes signing people out, blocking, password resets, the admin can't lock themselves out, a re-used email never inheriting old sign-ins), create / read / update / delete on every collection with its guards, the website's field limits matching the server's (`LimitsSyncTest`), status changes, working hours, the admin catalog (deactivation guard, the active switch keeping an artist's hours, logins staying blocked after a reset, the artist picker list), stats and moderation. Integration tests use a separate `tattoo_studio_test` database on the same cluster, so they need the same `secrets.properties`, and they run with a fixed clock.

The frontend has unit tests for the form checks and date helpers:

```bash
cd client
npm test
```

GitHub Actions runs both on every push (`.github/workflows/ci.yml`), the server tests against a temporary MongoDB.

## Project structure

```
client/                 React app: pages/ (public site), pages/staff/ (staff + admin screens),
                        features/ (booking flow state, shared staff helpers), lib/api.ts (the only place that calls the server)
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
- Fonts: Inter and Oswald (SIL Open Font License), bundled via Fontsource
- Built on the code of Fade Street (same author)
