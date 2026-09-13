package dev.jcpolicarpio.corepay.domain;

public class UnbalancedEntryException extends DomainException {

    public UnbalancedEntryException(String currency, long difference) {
        super("UNBALANCED_ENTRY",
                "Journal entry does not balance in " + currency
                        + ": debits and credits differ by " + difference + " minor units");
    }

    public UnbalancedEntryException(String message) {
        super("UNBALANCED_ENTRY", message);
    }
}
