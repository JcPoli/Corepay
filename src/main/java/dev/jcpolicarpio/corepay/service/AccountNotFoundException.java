package dev.jcpolicarpio.corepay.service;

import dev.jcpolicarpio.corepay.domain.DomainException;

public class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(String message) {
        super("ACCOUNT_NOT_FOUND", message);
    }
}
