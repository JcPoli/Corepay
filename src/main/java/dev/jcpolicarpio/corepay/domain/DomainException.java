package dev.jcpolicarpio.corepay.domain;

/** Base type for rule violations that are the caller's fault, not a bug. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
