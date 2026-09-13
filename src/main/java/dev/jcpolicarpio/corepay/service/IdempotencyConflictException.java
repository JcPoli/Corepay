package dev.jcpolicarpio.corepay.service;

import dev.jcpolicarpio.corepay.domain.DomainException;

/**
 * Same key, different body. Rejected rather than replayed: a client that
 * reuses a key for a different payment has a bug, and silently returning the
 * old response would lose the new payment.
 */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String key) {
        super("IDEMPOTENCY_CONFLICT",
                "Idempotency-Key " + key + " was already used with a different request body");
    }
}
