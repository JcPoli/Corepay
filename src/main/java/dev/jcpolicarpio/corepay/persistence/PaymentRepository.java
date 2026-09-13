package dev.jcpolicarpio.corepay.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByReference(String reference);

    Page<PaymentEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
