-- Core ledger schema.
--
-- Two rules drive every table here:
--   1. Value only moves through balanced journal entries.
--   2. Nothing is ever updated in place or deleted; corrections are new rows.
-- There is deliberately no balance column: a balance is derived from postings
-- so it can always be recomputed and reconciled.

CREATE TABLE accounts (
    id                   UUID         PRIMARY KEY,
    account_number       VARCHAR(34)  NOT NULL UNIQUE,
    holder_name          VARCHAR(120) NOT NULL,
    type                 VARCHAR(16)  NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    currency             VARCHAR(3)      NOT NULL,
    overdraft_limit_minor BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT accounts_type_check
        CHECK (type IN ('CUSTOMER', 'NOSTRO', 'SUSPENSE', 'INCOME')),
    CONSTRAINT accounts_status_check
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT accounts_overdraft_non_negative
        CHECK (overdraft_limit_minor >= 0)
);

CREATE TABLE journal_entries (
    id         UUID         PRIMARY KEY,
    reference  VARCHAR(64)  NOT NULL UNIQUE,
    narrative  VARCHAR(240) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE postings (
    id               BIGSERIAL   PRIMARY KEY,
    journal_entry_id UUID        NOT NULL REFERENCES journal_entries (id),
    account_id       UUID        NOT NULL REFERENCES accounts (id),
    direction        VARCHAR(6)  NOT NULL,
    amount_minor     BIGINT      NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT postings_direction_check CHECK (direction IN ('DEBIT', 'CREDIT')),
    -- Amounts are always positive; the direction carries the sign.
    CONSTRAINT postings_amount_positive CHECK (amount_minor > 0)
);

CREATE INDEX idx_postings_account ON postings (account_id);
CREATE INDEX idx_postings_entry ON postings (journal_entry_id);

CREATE TABLE payments (
    id                   UUID         PRIMARY KEY,
    reference            VARCHAR(64)  NOT NULL UNIQUE,
    status               VARCHAR(16)  NOT NULL,
    debtor_account_id    UUID         NOT NULL REFERENCES accounts (id),
    creditor_account_id  UUID         NOT NULL REFERENCES accounts (id),
    amount_minor         BIGINT       NOT NULL,
    currency             VARCHAR(3)      NOT NULL,
    remittance_info      VARCHAR(140),
    end_to_end_id        VARCHAR(35),
    journal_entry_id     UUID         REFERENCES journal_entries (id),
    reversal_entry_id    UUID         REFERENCES journal_entries (id),
    failure_reason       VARCHAR(240),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT payments_status_check
        CHECK (status IN ('INITIATED', 'VALIDATED', 'POSTED', 'SETTLED', 'REJECTED', 'REVERSED')),
    CONSTRAINT payments_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT payments_distinct_parties CHECK (debtor_account_id <> creditor_account_id)
);

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_debtor ON payments (debtor_account_id);
CREATE INDEX idx_payments_created ON payments (created_at DESC);

-- A retry of the same Idempotency-Key with the same body replays the original
-- response. The same key with a different body is a client bug and is
-- rejected: treating it as a duplicate would silently drop a payment.
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(120) PRIMARY KEY,
    fingerprint     VARCHAR(64)     NOT NULL,
    payment_id      UUID         REFERENCES payments (id),
    response_status INTEGER      NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Transactional outbox: events are written in the same transaction as the
-- ledger, then published separately. Kafka can be wired to this table without
-- touching the posting path.
CREATE TABLE outbox_events (
    id             BIGSERIAL    PRIMARY KEY,
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;
