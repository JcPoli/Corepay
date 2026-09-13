package dev.jcpolicarpio.corepay.service;

/** A request to move money between two accounts held on this ledger. */
public record TransferCommand(
        String debtorAccountNumber,
        String creditorAccountNumber,
        long amountMinor,
        String currency,
        String remittanceInfo,
        String endToEndId) {
}
