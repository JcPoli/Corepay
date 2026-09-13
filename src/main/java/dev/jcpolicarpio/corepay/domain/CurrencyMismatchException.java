package dev.jcpolicarpio.corepay.domain;

public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(String expected, String actual) {
        super("CURRENCY_MISMATCH",
                "Cannot combine amounts in " + expected + " and " + actual);
    }
}
