package dev.jcpolicarpio.corepay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID nostro = UUID.randomUUID();

    private List<Posting> book() {
        List<Posting> postings = new ArrayList<>();
        postings.addAll(JournalEntry.transfer(
                "OPEN", "funding", nostro, alice, Money.of(50000, "AED")).postings());
        postings.addAll(JournalEntry.transfer(
                "TRF", "rent", alice, bob, Money.of(1000, "AED")).postings());
        return postings;
    }

    @Test
    @DisplayName("balances are derived from postings, by account type")
    void balances() {
        List<Posting> book = book();
        assertThat(Ledger.balanceOf(alice, AccountType.CUSTOMER, "AED", book).minorUnits())
                .isEqualTo(49000);
        assertThat(Ledger.balanceOf(bob, AccountType.CUSTOMER, "AED", book).minorUnits())
                .isEqualTo(1000);
        // Funding grows both sides: the nostro asset is debited, so it rises.
        assertThat(Ledger.balanceOf(nostro, AccountType.NOSTRO, "AED", book).minorUnits())
                .isEqualTo(50000);
        assertThat(Ledger.balanceOf(UUID.randomUUID(), AccountType.CUSTOMER, "AED", book).minorUnits())
                .isZero();
    }

    @Test
    @DisplayName("a customer cannot overdraw without a limit")
    void overdraft() {
        assertThatCode(() -> Ledger.requireSufficientFunds(
                AccountType.CUSTOMER, Money.of(1000, "AED"), Money.of(1000, "AED"), 0))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> Ledger.requireSufficientFunds(
                AccountType.CUSTOMER, Money.of(1000, "AED"), Money.of(1001, "AED"), 0))
                .isInstanceOf(InsufficientFundsException.class);

        assertThatCode(() -> Ledger.requireSufficientFunds(
                AccountType.CUSTOMER, Money.of(1000, "AED"), Money.of(1500, "AED"), 50000))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> Ledger.requireSufficientFunds(
                AccountType.CUSTOMER, Money.of(0, "AED"), Money.of(50001, "AED"), 50000))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("internal accounts may go negative intraday")
    void internalAccounts() {
        assertThatCode(() -> Ledger.requireSufficientFunds(
                AccountType.SUSPENSE, Money.of(0, "AED"), Money.of(999999, "AED"), 0))
                .doesNotThrowAnyException();
    }
}
