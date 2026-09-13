package dev.jcpolicarpio.corepay.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyRecordEntity() {
    }

    public IdempotencyRecordEntity(String idempotencyKey, String fingerprint, UUID paymentId,
                                   int responseStatus, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.fingerprint = fingerprint;
        this.paymentId = paymentId;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
