package dev.jcpolicarpio.corepay.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {
}
