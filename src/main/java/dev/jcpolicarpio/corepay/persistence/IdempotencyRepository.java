package dev.jcpolicarpio.corepay.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecordEntity, String> {
}
