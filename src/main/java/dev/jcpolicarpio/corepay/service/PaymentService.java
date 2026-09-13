package dev.jcpolicarpio.corepay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jcpolicarpio.corepay.domain.IdempotencyFingerprint;
import dev.jcpolicarpio.corepay.domain.JournalEntry;
import dev.jcpolicarpio.corepay.domain.Money;
import dev.jcpolicarpio.corepay.domain.PaymentStatus;
import dev.jcpolicarpio.corepay.persistence.AccountEntity;
import dev.jcpolicarpio.corepay.persistence.AccountRepository;
import dev.jcpolicarpio.corepay.persistence.PaymentEntity;
import dev.jcpolicarpio.corepay.persistence.PaymentRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment orchestration.
 *
 * The lifecycle is explicit — INITIATED, VALIDATED, POSTED, then SETTLED or
 * REVERSED — because in payments the illegal transitions are the expensive
 * ones. Posting and the idempotency record commit in the same transaction, so
 * a retry can never produce a second set of postings.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository payments,
                          AccountRepository accounts,
                          LedgerService ledger,
                          IdempotencyService idempotency,
                          OutboxService outbox,
                          ObjectMapper objectMapper) {
        this.payments = payments;
        this.accounts = accounts;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** A replayed result carries the original payment untouched. */
    public record TransferOutcome(PaymentResult result, boolean replayed) {
    }

    @Transactional
    public TransferOutcome transfer(TransferCommand command, String idempotencyKey) {
        String fingerprint = IdempotencyFingerprint.of(canonical(command));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> replay = idempotency.replayFor(idempotencyKey, fingerprint);
            if (replay.isPresent()) {
                return new TransferOutcome(deserialize(replay.get()), true);
            }
        }

        AccountEntity debtor = requireAccount(command.debtorAccountNumber());
        AccountEntity creditor = requireAccount(command.creditorAccountNumber());

        Money amount = Money.of(command.amountMinor(), command.currency());
        if (!amount.isPositive()) {
            throw new InvalidPaymentException("Transfer amount must be positive");
        }
        if (debtor.getId().equals(creditor.getId())) {
            throw new InvalidPaymentException("Debtor and creditor must be different accounts");
        }

        String reference = nextReference();
        PaymentEntity payment = payments.save(new PaymentEntity(
                UUID.randomUUID(), reference, debtor.getId(), creditor.getId(),
                amount.minorUnits(), amount.currency(),
                command.remittanceInfo(), command.endToEndId()));

        // Currency and account-status checks happen inside the ledger against
        // freshly locked rows; reaching VALIDATED only means the request shape
        // is sound.
        payment.transitionTo(PaymentStatus.VALIDATED);

        UUID journalEntryId = ledger.post(JournalEntry.transfer(
                reference,
                command.remittanceInfo() == null ? "" : command.remittanceInfo(),
                debtor.getId(), creditor.getId(), amount));

        payment.setJournalEntryId(journalEntryId);
        payment.transitionTo(PaymentStatus.POSTED);

        PaymentResult result = toResult(payment, debtor, creditor);
        outbox.record("payment", payment.getId(), "payment.posted", Map.of(
                "paymentId", payment.getId().toString(),
                "reference", reference,
                "amountMinor", amount.minorUnits(),
                "currency", amount.currency()));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.remember(idempotencyKey, fingerprint, payment.getId(), 201, serialize(result));
        }
        return new TransferOutcome(result, false);
    }

    @Transactional
    public PaymentResult settle(UUID paymentId) {
        PaymentEntity payment = requirePayment(paymentId);
        payment.transitionTo(PaymentStatus.SETTLED);
        outbox.record("payment", payment.getId(), "payment.settled",
                Map.of("paymentId", payment.getId().toString()));
        return toResult(payment);
    }

    /**
     * Reversal posts the mirror image of the original entry. The original
     * postings stay exactly where they are — an auditor must be able to see
     * both the mistake and the correction.
     */
    @Transactional
    public PaymentResult reverse(UUID paymentId, String reason) {
        PaymentEntity payment = requirePayment(paymentId);
        if (payment.getJournalEntryId() == null) {
            throw new InvalidPaymentException("Payment " + paymentId + " was never posted");
        }
        AccountEntity debtor = requireAccountById(payment.getDebtorAccountId());
        AccountEntity creditor = requireAccountById(payment.getCreditorAccountId());
        Money amount = Money.of(payment.getAmountMinor(), payment.getCurrency());

        JournalEntry original = JournalEntry.transfer(
                payment.getReference(), "", debtor.getId(), creditor.getId(), amount);
        UUID reversalId = ledger.post(original.reversal(payment.getReference() + "-REV"));

        payment.setReversalEntryId(reversalId);
        payment.setFailureReason(reason);
        payment.transitionTo(PaymentStatus.REVERSED);
        outbox.record("payment", payment.getId(), "payment.reversed", Map.of(
                "paymentId", payment.getId().toString(),
                "reason", reason == null ? "" : reason));
        return toResult(payment, debtor, creditor);
    }

    @Transactional(readOnly = true)
    public PaymentResult get(UUID paymentId) {
        return toResult(requirePayment(paymentId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResult> list(Pageable pageable) {
        return payments.findAllByOrderByCreatedAtDesc(pageable).map(this::toResult);
    }

    private PaymentEntity requirePayment(UUID id) {
        return payments.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("No payment with id " + id));
    }

    private AccountEntity requireAccount(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("No account " + accountNumber));
    }

    private AccountEntity requireAccountById(UUID id) {
        return accounts.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("No account with id " + id));
    }

    private PaymentResult toResult(PaymentEntity payment) {
        return toResult(payment,
                requireAccountById(payment.getDebtorAccountId()),
                requireAccountById(payment.getCreditorAccountId()));
    }

    private PaymentResult toResult(PaymentEntity payment, AccountEntity debtor, AccountEntity creditor) {
        OffsetDateTime created = payment.getCreatedAt() == null
                ? OffsetDateTime.now() : payment.getCreatedAt();
        OffsetDateTime updated = payment.getUpdatedAt() == null ? created : payment.getUpdatedAt();
        return new PaymentResult(
                payment.getId(),
                payment.getReference(),
                payment.getStatus().name(),
                debtor.getAccountNumber(),
                creditor.getAccountNumber(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getRemittanceInfo(),
                payment.getEndToEndId(),
                payment.getJournalEntryId(),
                payment.getReversalEntryId(),
                payment.getFailureReason(),
                created.toString(),
                updated.toString());
    }

    /** Stable field order so the same request always hashes identically. */
    private String canonical(TransferCommand command) {
        return String.join("|",
                nullSafe(command.debtorAccountNumber()),
                nullSafe(command.creditorAccountNumber()),
                Long.toString(command.amountMinor()),
                nullSafe(command.currency()),
                nullSafe(command.remittanceInfo()),
                nullSafe(command.endToEndId()));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String nextReference() {
        return "PMT-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + Long.toString(ThreadLocalRandom.current().nextLong(100000L, 999999L));
    }

    private String serialize(PaymentResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PaymentResult is not serialisable", e);
        }
    }

    private PaymentResult deserialize(String body) {
        try {
            return objectMapper.readValue(body, PaymentResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable", e);
        }
    }
}
