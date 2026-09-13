package dev.jcpolicarpio.corepay.web;

import dev.jcpolicarpio.corepay.service.PaymentResult;
import dev.jcpolicarpio.corepay.service.PaymentService;
import dev.jcpolicarpio.corepay.service.TransferCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @Operation(summary = "Initiate and post an internal transfer",
            description = """
                    Send an Idempotency-Key header. Repeating the same key with the same body
                    replays the original response and posts nothing further; the same key with
                    a different body is rejected with 409.
                    """)
    public ResponseEntity<PaymentResult> transfer(
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Parameter(description = "Client-generated key, e.g. a UUID") String idempotencyKey,
            @Valid @RequestBody Api.TransferRequest request) {

        PaymentService.TransferOutcome outcome = payments.transfer(
                new TransferCommand(
                        request.debtorAccountNumber(),
                        request.creditorAccountNumber(),
                        request.amountMinor(),
                        request.currency(),
                        request.remittanceInfo(),
                        request.endToEndId()),
                idempotencyKey);

        // A replay is not a new resource, so it answers 200 rather than 201.
        HttpStatus status = outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header("Idempotent-Replay", Boolean.toString(outcome.replayed()))
                .body(outcome.result());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one payment")
    public PaymentResult get(@PathVariable UUID id) {
        return payments.get(id);
    }

    @GetMapping
    @Operation(summary = "List payments, newest first")
    public Api.PageResponse<PaymentResult> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PaymentResult> found = payments.list(PageRequest.of(page, Math.min(size, 100)));
        return new Api.PageResponse<>(
                found.getContent(), found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Mark a posted payment as settled")
    public PaymentResult settle(@PathVariable UUID id) {
        return payments.settle(id);
    }

    @PostMapping("/{id}/reverse")
    @Operation(summary = "Reverse a posted or settled payment",
            description = "Posts the mirror entry. The original postings are never altered.")
    public PaymentResult reverse(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) Api.ReverseRequest request) {
        return payments.reverse(id, request == null ? null : request.reason());
    }
}
