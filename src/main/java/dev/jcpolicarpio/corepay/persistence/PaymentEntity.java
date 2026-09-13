package dev.jcpolicarpio.corepay.persistence;

import dev.jcpolicarpio.corepay.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "debtor_account_id", nullable = false)
    private UUID debtorAccountId;

    @Column(name = "creditor_account_id", nullable = false)
    private UUID creditorAccountId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "remittance_info")
    private String remittanceInfo;

    @Column(name = "end_to_end_id")
    private String endToEndId;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "reversal_entry_id")
    private UUID reversalEntryId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentEntity() {
    }

    public PaymentEntity(UUID id, String reference, UUID debtorAccountId, UUID creditorAccountId,
                         long amountMinor, String currency, String remittanceInfo, String endToEndId) {
        this.id = id;
        this.reference = reference;
        this.status = PaymentStatus.INITIATED;
        this.debtorAccountId = debtorAccountId;
        this.creditorAccountId = creditorAccountId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.remittanceInfo = remittanceInfo;
        this.endToEndId = endToEndId;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * The only way the status changes. The domain enum decides whether the
     * move is legal, so the rule lives in one place and is unit-testable
     * without a database.
     */
    public void transitionTo(PaymentStatus next) {
        this.status.requireTransitionTo(next);
        this.status = next;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public UUID getDebtorAccountId() {
        return debtorAccountId;
    }

    public UUID getCreditorAccountId() {
        return creditorAccountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRemittanceInfo() {
        return remittanceInfo;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public void setJournalEntryId(UUID journalEntryId) {
        this.journalEntryId = journalEntryId;
    }

    public UUID getReversalEntryId() {
        return reversalEntryId;
    }

    public void setReversalEntryId(UUID reversalEntryId) {
        this.reversalEntryId = reversalEntryId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
