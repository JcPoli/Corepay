package dev.jcpolicarpio.corepay.service;

import dev.jcpolicarpio.corepay.persistence.IdempotencyRecordEntity;
import dev.jcpolicarpio.corepay.persistence.IdempotencyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository records;

    public IdempotencyService(IdempotencyRepository records) {
        this.records = records;
    }

    /**
     * Returns the stored response for a repeat of the same request.
     * Same key with a different body is a conflict, not a duplicate.
     */
    @Transactional(readOnly = true)
    public Optional<String> replayFor(String key, String fingerprint) {
        return records.findById(key).map(record -> {
            if (!record.getFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(key);
            }
            return record.getResponseBody();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remember(String key, String fingerprint, UUID paymentId, int status, String body) {
        records.save(new IdempotencyRecordEntity(key, fingerprint, paymentId, status, body));
    }
}
