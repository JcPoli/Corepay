package dev.jcpolicarpio.corepay.web;

import dev.jcpolicarpio.corepay.domain.AccountUnavailableException;
import dev.jcpolicarpio.corepay.domain.CurrencyMismatchException;
import dev.jcpolicarpio.corepay.domain.DomainException;
import dev.jcpolicarpio.corepay.domain.InsufficientFundsException;
import dev.jcpolicarpio.corepay.domain.InvalidTransitionException;
import dev.jcpolicarpio.corepay.domain.UnbalancedEntryException;
import dev.jcpolicarpio.corepay.service.AccountNotFoundException;
import dev.jcpolicarpio.corepay.service.IdempotencyConflictException;
import dev.jcpolicarpio.corepay.service.InvalidPaymentException;
import dev.jcpolicarpio.corepay.service.PaymentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain failures to status codes.
 *
 * Rule violations answer 4xx with a stable machine-readable code, because a
 * client retrying an insufficient-funds error forever is worse than a clear
 * refusal. Unexpected faults answer 500 and say nothing else: an error body
 * is not the place to leak internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({AccountNotFoundException.class, PaymentNotFoundException.class})
    public ResponseEntity<Api.ErrorResponse> notFound(DomainException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e, request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Api.ErrorResponse> conflict(DomainException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e, request);
    }

    @ExceptionHandler({
            InsufficientFundsException.class,
            AccountUnavailableException.class,
            InvalidTransitionException.class})
    public ResponseEntity<Api.ErrorResponse> unprocessable(DomainException e, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e, request);
    }

    @ExceptionHandler({
            CurrencyMismatchException.class,
            UnbalancedEntryException.class,
            InvalidPaymentException.class})
    public ResponseEntity<Api.ErrorResponse> badRequest(DomainException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Api.ErrorResponse> validation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new Api.ErrorResponse("VALIDATION_FAILED", detail, request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Api.ErrorResponse> illegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new Api.ErrorResponse("BAD_REQUEST", e.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Api.ErrorResponse> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled failure on {}", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new Api.ErrorResponse("INTERNAL_ERROR",
                        "The request could not be completed", request.getRequestURI()));
    }

    private ResponseEntity<Api.ErrorResponse> build(
            HttpStatus status, DomainException e, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new Api.ErrorResponse(e.code(), e.getMessage(), request.getRequestURI()));
    }
}
