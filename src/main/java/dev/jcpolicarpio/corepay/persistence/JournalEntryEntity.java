package dev.jcpolicarpio.corepay.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Column(nullable = false)
    private String narrative;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected JournalEntryEntity() {
    }

    public JournalEntryEntity(UUID id, String reference, String narrative) {
        this.id = id;
        this.reference = reference;
        this.narrative = narrative;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getNarrative() {
        return narrative;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
