# corepay — notes for AI-assisted sessions

## What this repo is

A payments core with a double-entry ledger. It is JC Policarpio's public
evidence of professional Java work: he is a software engineer of ~3 years in
banking (core banking, payments, internal operations) using Spring Boot and
PostgreSQL, and this repo exists because that work is not public. It is aimed
at hiring managers in UAE banking, so it must read as production-minded rather
than as a tutorial project.

**Nothing here may come from his employer's codebase.** Everything is written
from scratch, generic, with no client names, no internal logic, and no real
account data. That constraint is not negotiable.

## The five invariants — do not weaken these

1. **No stored balances.** A balance is derived from postings, signed by
   `AccountType.normalSide()`. Never add a `balance` column or cache; if
   performance ever demands one, it is a projection and never the source of
   truth.
2. **Value moves only through balanced journal entries.** `JournalEntry`
   validates debits equal credits *per currency* in its factory. Never add a
   path that writes `PostingEntity` rows outside `LedgerService.post`.
3. **Append-only ledger.** No updates or deletes on `postings` or
   `journal_entries`. Corrections are reversal entries.
4. **Idempotency is transactional.** The idempotency record commits with the
   postings. Same key + same fingerprint replays; same key + different
   fingerprint is a 409, never a silent duplicate.
5. **Money is integer minor units.** No `double`, no `BigDecimal` in the
   domain, no decimal strings on the wire.

## Conventions

- Java 17 is the language floor (the jar runs on 17, 21 or newer — the dev
  machine's JDK moves between 18 and 21). Spring Boot 3.3.x, Maven.
- `domain/` has **no Spring, JPA or Jackson imports**. That is what makes the
  rules unit-testable in milliseconds, and it is also how the logic gets
  verified offline. Keep it pure.
- Flyway owns the schema; Hibernate is `ddl-auto: validate`. New schema
  changes are new `V__` migrations, never edits to an applied one.
- Integration tests use Testcontainers against real PostgreSQL. Do not
  substitute H2: row locks, check constraints and transaction semantics are
  exactly what is being tested.
- Authorisation is scope-based (`payments:write`, `accounts:read`, …), not
  role-based.
- Sign rules live in `AccountType`, never duplicated into SQL. The posting
  query returns debits-minus-credits and the service applies the sign.

## Verification without a build

The container used for assistance has a JRE with the compiler module but no
`javac` and no network, so Maven cannot run. The domain layer is deliberately
dependency-free so its **shipped class bodies** can be concatenated into a
single-file harness and executed with `java Foo.java`. That harness is how the
ledger maths, the balance invariant, the state machine and the fingerprint
were verified. Everything Spring-dependent is unverified until JC runs
`mvn verify` — expect iteration there, and fix compile errors before
touching design.

## Deployment

Render blueprint in `render.yaml` (Docker build, free Postgres, generated
`JWT_SECRET`). Free instances sleep, so the first request is slow. CI runs the
test suite plus two Trivy scans (filesystem and image), failing on HIGH or
CRITICAL and uploading SARIF.

## Honest framing

The README has a "Not in this version" section listing S3 export, Kafka, real
payment rails and FX as explicitly out of scope. Keep it accurate. If a
feature gets added, move it out of that list; never let the list become wrong
in the flattering direction.
