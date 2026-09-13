package dev.jcpolicarpio.corepay.service;

import dev.jcpolicarpio.corepay.domain.DomainException;

public class PaymentNotFoundException extends DomainException {

    public PaymentNotFoundException(String message) {
        super("PAYMENT_NOT_FOUND", message);
    }
}
