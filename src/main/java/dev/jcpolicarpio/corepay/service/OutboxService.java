package dev.jcpolicarpio.corepay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jcpolicarpio.corepay.persistence.OutboxEventEntity;
import dev.jcpolicarpio.corepay.persistence.OutboxRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox. Events are written in the ledger's transaction and
 * drained afterwards, so a published event always corresponds to a committed
 * posting. Swapping the log line below for a Kafka producer is the only change
 * needed to make this a real event stream.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        outbox.save(new OutboxEventEntity(aggregateType, aggregateId, eventType, serialize(payload)));
    }

    @Scheduled(fixedDelayString = "${corepay.outbox.drain-interval-ms:5000}")
    @Transactional
    public void drain() {
        List<OutboxEventEntity> pending =
                outbox.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEventEntity event : pending) {
            log.info("outbox publish type={} aggregate={} payload={}",
                    event.getEventType(), event.getAggregateId(), event.getPayload());
            event.markPublished();
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload is not serialisable", e);
        }
    }
}
