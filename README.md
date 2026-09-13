# corepay

A payments core with a double-entry ledger, written to show how money
movement is handled when getting it wrong is expensive.

Built with Java 17, Spring Boot 3, PostgreSQL and Flyway, tested against a
real database with Testcontainers, containerised, and scanned for
vulnerabilities in CI with Trivy.

Live API and Swagger UI: _add your Render URL here_ → `/swagger-ui.html`

---

## What it demonstrates

This is not a CRUD service with a `balance` column. Five decisions carry the
weight, and each one is testable:

**Balances are derived, never stored.** A balance is the sum of an account's
postings, signed by the account's normal side. Nothing can drift, and any
balance can be reconciled from history at any time.

**Value only moves through balanced journal entries.** `JournalEntry` enforces
debits equal credits *per currency* in its constructor, so an unbalanced entry
cannot be built, let alone persisted. A cross-currency leg pair is rejected as
unbalanced rather than quietly netted.

**Retries are idempotent.** `POST /payments` takes an `Idempotency-Key`. The
key is stored with a SHA-256 fingerprint of the request and the original
response, in the same transaction as the postings. The same key with the same
body replays; the same key with a *different* body answers 409, because
treating that as a duplicate would silently drop a payment.

**Concurrent debits cannot overdraw an account.** Accounts are locked
`FOR UPDATE` in a deterministic order before posting, so two simultaneous
transfers serialise instead of both reading the same balance. There is an
integration test that fires eight parallel transfers at an account holding
five transfers' worth and asserts exactly five succeed.

**Nothing is edited or deleted.** A posted payment is corrected by a reversal
entry that mirrors the original. Both stay in the ledger, because an auditor
needs to see the mistake *and* the correction.

Alongside those: an explicit payment state machine (`INITIATED → VALIDATED →
POSTED → SETTLED | REVERSED`, with the illegal moves enumerated), a
transactional outbox so events can never describe a posting that did not
commit, scope-based JWT authorisation, and money held exclusively as integer
minor units.

## Domain model

```
accounts ──────┐
               │         journal_entries ──── postings (append-only)
payments ──────┤              ▲                   │
   │           │              └───────────────────┘
   │           │
   ├─ journal_entry_id      idempotency_records
   └─ reversal_entry_id     outbox_events
```

Account types decide which direction increases a balance: `CUSTOMER` deposits
are a liability (credits increase), `NOSTRO` is an asset (debits increase).
That rule lives in one enum, not in SQL, so there is a single definition of
"positive".

## Running it

```bash
docker compose up --build
```

Then open `http://localhost:8080/swagger-ui.html`, call
`POST /api/v1/auth/token` with `teller` / `teller-demo`, press **Authorize**,
and move some money between the seeded accounts.

Without Docker, you need a PostgreSQL on `localhost:5432` and:

```bash
mvn spring-boot:run
```

Tests (Testcontainers starts its own PostgreSQL, so Docker must be running):

```bash
mvn verify
```

There is no committed Maven wrapper; generate one with
`mvn -N wrapper:wrapper` if you want `./mvnw`.

## Try the interesting parts

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"teller","password":"teller-demo"}' | jq -r .accessToken)

# Post a transfer. 125000 minor units = AED 1,250.00
KEY=$(uuidgen)
curl -s localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' \
  -d '{"debtorAccountNumber":"AE070331234567890123456",
       "creditorAccountNumber":"AE070331234567890123457",
       "amountMinor":125000,"currency":"AED","remittanceInfo":"Invoice 118"}'

# Same key, same body: replays, posts nothing new, answers 200.
# Same key, different amount: answers 409 IDEMPOTENCY_CONFLICT.

curl -s localhost:8080/api/v1/accounts -H "Authorization: Bearer $TOKEN"
```

## Security

Stateless JWT (HS256) with scopes rather than roles: `payments:write`,
`payments:read`, `accounts:write`, `accounts:read`. The demo `teller` holds
all four; `auditor` holds the read scopes only, and is refused with 403 on any
write. A real deployment would federate to the bank's identity provider —
`AuthController` exists so the API is explorable from Swagger without extra
infrastructure, and it is the only unauthenticated write endpoint.

Set `JWT_SECRET` in every environment. Hibernate runs with
`ddl-auto: validate`: Flyway owns the schema, because an ORM that can alter a
ledger table is an audit finding waiting to happen.

## CI and supply chain

`.github/workflows/ci.yml` builds, runs the full test suite against a real
PostgreSQL, then runs Trivy twice: a filesystem scan for vulnerable
dependencies, secrets and misconfiguration, and an image scan of the built
container. Both fail the pipeline on HIGH or CRITICAL, and image findings are
uploaded as SARIF so they appear in the repository's Security tab.

## Vulnerability posture

CI fails the build on HIGH and CRITICAL findings, with `ignore-unfixed`
enabled so only findings with a released fix can break it. Where a CVE
demands a version newer than Spring Boot manages, the dependency is pinned
explicitly — see the `tomcat.version` and `postgresql.version` properties in
`pom.xml`.

The initial scan reported 40 findings. Upgrading Spring Boot and pinning
Tomcat and pgjdbc cleared 37 of them. The remaining three are Tomcat CVEs
with no published fix, accepted in `.trivyignore` with the reason each is
unreachable in this service and a review date. A suppression here is a
decision with an owner and an expiry, not a way to silence the pipeline.

## Deploying

`render.yaml` is a Render blueprint: it builds the Dockerfile, provisions a
PostgreSQL, injects the connection string, and generates a JWT secret. Free
instances sleep when idle, so the first request after a quiet spell takes
roughly half a minute.

Any container host works the same way — the app needs `DATABASE_URL`,
`DATABASE_USER`, `DATABASE_PASSWORD` and `JWT_SECRET`, and exposes
`/actuator/health/readiness` for probes.

## Not in this version

Called out deliberately, because a demo that pretends to be complete is worse
than one that says where it stops:

- **Statement export to S3.** `AccountService.statement` produces the data; the
  storage adapter is not wired. Adding it is an `S3Client` bean plus a
  `PutObjectRequest`, gated on `corepay.statements.storage=s3`.
- **Kafka.** The outbox table and drain loop exist and the publisher logs;
  swapping the log line for a producer is the only change needed.
- **Real card or SWIFT rails.** Transfers are book transfers between accounts
  on this ledger. The request shape borrows ISO 20022 vocabulary
  (`endToEndId`, `remittanceInfo`) but this is not an ISO 20022 gateway.
- **Multi-currency FX.** The ledger can hold balanced multi-currency entries;
  there is no rate source, so no conversion is performed.

## Layout

```
src/main/java/dev/jcpolicarpio/corepay/
  domain/        pure, framework-free: Money, Posting, JournalEntry, Ledger,
                 PaymentStatus, exceptions. No Spring imports, no database.
  service/       LedgerService (the only writer of postings), PaymentService,
                 AccountService, IdempotencyService, OutboxService
  persistence/   JPA entities and Spring Data repositories
  web/           controllers, request/response records, error mapping
  config/        security, OpenAPI, typed configuration
src/main/resources/db/migration/   Flyway schema and demo data
src/test/java/...                  domain unit tests, Testcontainers ITs
```

The domain package has no framework dependencies on purpose: the rules that
matter can be unit-tested in milliseconds without a database, and they read as
banking rules rather than as Spring code.
