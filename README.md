# core-services

A modular Kotlin/Spring Boot application for reusable authentication, notifications, phone verification and one-off payments. It uses PostgreSQL and Flyway; the modules run in one application, not as microservices.

## Run locally

Prerequisites: JDK 21+, Docker Compose, and a shell. Local mode binds the app to `127.0.0.1`, disables the Secure cookie flag for localhost, uses fake payment and notification providers, and exposes a temporary development mailbox. It is not a production profile.

```sh
cp .env.example .env
# Generate three distinct passwords with `openssl rand -hex 32`; keep the DATABASE_PASSWORD and FLYWAY_PASSWORD copies in sync with their POSTGRES_* values.
# Fill in provider credentials only when testing those integrations.
docker compose up -d postgres
set -a
. ./.env
set +a
./gradlew bootRun
```

Open <http://localhost:8080>. The first PostgreSQL initialization creates separate admin, migration and application roles. The app connects as `core_services_app`; Flyway connects as `core_services_migrator`. The PostgreSQL port is published only on loopback. The init script runs only when the data volume is first created.

In local mode, the fake providers keep complete message content in process memory for up to 30 minutes. Retrieve an OTP or password-reset link with `GET /dev/mailbox?recipient=<exact-email-or-phone>`. This endpoint is present only in `local`/`test`, and local mode is bound to loopback. Messages are not written to logs or the database; restarting the app clears the mailbox. Never expose the local profile to a network.

Stop the database with `docker compose down`. `docker compose down -v` also permanently deletes the local database volume.

## Profiles and configuration

The `local` profile is the default. Tests use `test` and Testcontainers PostgreSQL; no live provider credentials are required. Use `prod` explicitly for real provider adapters:

```sh
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

Set the variables shown in `.env.example` before using production adapters. `OTP_HMAC_SECRET` must be at least 32 bytes. Stripe requires an `sk_test_...` key and a matching webhook secret: live Stripe keys are deliberately rejected until security review. Payment prices are server-side; the demo catalog currently exposes only `demo-item`, configured with `DEMO_ITEM_PRICE_MINOR` and `DEMO_ITEM_CURRENCY` (default 1000 USD cents). Never accept amount or currency from the browser.

Production must terminate HTTPS at a trusted reverse proxy, set `APP_BASE_URL`, `PAYMENT_SUCCESS_URL`, and `PAYMENT_CANCEL_URL` to HTTPS origins, set SMTP/Twilio/WhatsApp credentials, and secure the PostgreSQL host and backups. Configure only trusted proxies to set forwarded headers. WhatsApp text sends work only within Meta's customer-service window; approved template messages for business-initiated delivery are not implemented.

## Implemented API

| Endpoint | Access / behavior |
|---|---|
| `GET /actuator/health` | Anonymous health only |
| `GET /api/csrf` | Returns the CSRF cookie token and header name |
| `POST /api/auth/register`, `/login`, `/logout` | Email/password; server-side session cookie |
| `POST /api/auth/password-reset/request`, `/confirm` | Generic request response; expiring single-use reset token |
| `GET /api/me` | Authenticated user |
| `GET /api/phone/verification`, `POST /start`, `/confirm` | Authenticated E.164 phone verification by OTP |
| `GET /api/notifications`, `/preferences`; `PUT /preferences` | Own notification history and channel preferences |
| `POST /api/payments`, `GET /api/payments`, `GET /api/payments/{id}` | Own payments; create only a configured product checkout |
| `POST /api/webhooks/stripe` | Stripe signature-verified webhook; CSRF-exempt only for this endpoint |
| `GET /v3/api-docs` | OpenAPI JSON |
| `/swagger-ui.html` | Swagger UI in local profile only |

State-changing browser requests must use the token returned by `/api/csrf` in the returned header (default `X-XSRF-TOKEN`). The UI does this automatically.

## Build and tests

```sh
./gradlew test
./gradlew bootJar
```

Integration tests use Testcontainers and require Docker. Unit tests do not require live email, messaging or payment credentials. GitHub Actions runs the tests, CodeQL, Gitleaks, and submits the Gradle dependency graph for Dependabot alerts.

## Security and known limitations

- Passwords use Argon2id with 19 MiB memory, two iterations and one lane. Reset tokens are random 256-bit values; only SHA-256 hashes are stored. OTPs are six-digit random codes; only an HMAC is stored, bound to the challenge and phone number.
- Sessions are server-side, HTTP-only, SameSite=Lax cookies; Secure is disabled only in local/test. Login rotates the session ID. Password reset expires registered sessions. Sessions are in-memory and are lost on restart; multi-instance deployments need Spring Session JDBC or another shared session store.
- Login, registration and reset rate buckets are bounded, per-instance in-memory windows. Messaging rate limits use PostgreSQL. Client IPs use the direct connection address; configure a trusted proxy deliberately if the app is behind one.
- Reset requests are processed asynchronously and return the same message for existing and unknown addresses. Registration reports duplicate emails (409); email ownership verification is not implemented.
- Email, SMS and WhatsApp provider interfaces are implemented; outbound non-local provider errors are retried a bounded number of times. Recipients and delivery metadata are stored, but message bodies, OTPs, reset tokens, and raw payment webhook payloads are not.
- Stripe status changes only on signature-verified events; event IDs are idempotent, payment amount/currency/session are matched, and terminal payment states are not changed. The local checkout is fake and cannot mark a payment as paid.
- In-memory login rate limits and sessions are per instance. Notifications are sent synchronously except password-reset processing. There is no message queue, email ownership verification, OTP phone uniqueness, or WhatsApp template support.
- This code has not had an independent security review. Do not treat it as production-ready until deployment-specific security review, migrations/backup restore tests, provider sandbox checks, and operational monitoring are complete.

## Licence

No licence has been chosen yet.
