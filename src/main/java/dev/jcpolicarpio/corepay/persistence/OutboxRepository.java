package dev.jcpolicarpio.corepay.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);

    long countByPublishedAtIsNull();
}
