package dev.jcpolicarpio.corepay.service;

import java.util.UUID;

/**
 * What the API says about a payment. Timestamps are ISO-8601 strings so the
 * same shape round-trips through the idempotency store without needing extra
 * Jackson configuration.
 */
public record PaymentResult(
        UUID id,
        String reference,
        String status,
        String debtorAccountNumber,
        String creditorAccountNumber,
        long amountMinor,
        String currency,
        String remittanceInfo,
        String endToEndId,
        UUID journalEntryId,
        UUID reversalEntryId,
        String failureReason,
        String createdAt,
        String updatedAt) {
}
