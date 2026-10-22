# core-services

A reusable application foundation: authentication, notifications and one-off payments in a single modular Kotlin and Spring Boot application backed by PostgreSQL. The aim is to stop rebuilding these three concerns for every new product.

> **Status: planning and scaffolding only.** The repository currently contains the Gradle wrapper, [PLAN.md](PLAN.md) and this README. There is no application code, database migration, Docker Compose file or test yet, so nothing has been built, run or security-reviewed. It is not production-ready.

## Planned scope

| Module | Planned capabilities |
|---|---|
| Authentication | Email and password registration, login and logout with server-managed cookie sessions, expiring single-use password reset, protected `GET /api/me`, phone verification by OTP, rate limiting, CSRF protection, minimal demo UI |
| Notifications | Reusable service with Email, SMS and WhatsApp provider interfaces; delivery attempts, status and errors recorded in PostgreSQL; bounded retries; consent and preferences; rate limits |
| Payments | Stripe Checkout one-off payments (test mode first); verified webhooks; safe handling of duplicate events; amounts as integer minor units; ownership checks |
| Platform | Flyway migrations, Docker Compose for local PostgreSQL, OpenAPI documentation, automated tests that need no live provider credentials, CI |

Out of scope: phone-number/SMS login, Google/Microsoft sign-in, passkeys, subscriptions and complex refunds, complex roles/organisations/multi-tenancy, a polished frontend, a message queue.

## Technology (chosen, not yet exercised)

Kotlin 2.3.21, Spring Boot 4.1.1 with Spring Security, PostgreSQL 17, Flyway, Gradle 9.8.2 (wrapper), Docker Compose, JUnit, MockK and Testcontainers, springdoc-openapi, Stripe Java SDK. See [PLAN.md](PLAN.md#3-technology) for the full list and versions.

## Repository contents today

```
.
├── .gitattributes
├── .gitignore
├── PLAN.md
├── README.md
├── gradle/wrapper/
├── gradlew
└── gradlew.bat
```

## Prerequisites for the planned build

- JDK 21 or newer
- Docker with Compose (local PostgreSQL and Testcontainers)

## Getting started

Not available yet. The build script, Docker Compose file and application code arrive in Phase 1 of [PLAN.md](PLAN.md). The setup, configuration, migration, test and deployment instructions will be added here once the commands have actually been run and verified.

## Security principles (design commitments, not yet implemented)

- Use Spring Security and maintained libraries; never invent cryptography.
- Store only password hashes, reset-token hashes and OTP hashes.
- Keep secrets out of Git; configuration comes from environment variables with no default secrets.
- Do not expose PostgreSQL publicly; use a least-privilege database account.
- Use HTTPS in production.

## Roadmap

[PLAN.md](PLAN.md) holds the phases, data model, API outline, security decisions, test plan, assumptions, risks and the verification log.

## Licence

No licence has been chosen yet.
