package dev.jcpolicarpio.corepay.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID fees = UUID.randomUUID();
    private final UUID nostro = UUID.randomUUID();

    @Test
    @DisplayName("an unbalanced entry cannot be constructed")
    void mustBalance() {
        assertThatThrownBy(() -> JournalEntry.of("X", "", List.of(
                Posting.debit(alice, Money.of(1000, "AED")),
                Posting.credit(bob, Money.of(999, "AED")))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    @DisplayName("a single leg is not an entry")
    void needsTwoLegs() {
        assertThatThrownBy(() -> JournalEntry.of("X", "", List.of(
                Posting.debit(alice, Money.of(1000, "AED")))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    @DisplayName("a three-legged entry with a fee still balances")
    void feeEntry() {
        JournalEntry entry = JournalEntry.of("TRF", "with fee", List.of(
                Posting.debit(alice, Money.of(1050, "AED")),
                Posting.credit(bob, Money.of(1000, "AED")),
                Posting.credit(fees, Money.of(50, "AED"))));
        assertThat(entry.postings()).hasSize(3);
        assertThat(entry.totalDebitMinorUnits()).isEqualTo(1050);
    }

    @Test
    @DisplayName("balance is required per currency, not in aggregate")
    void perCurrency() {
        assertThatThrownBy(() -> JournalEntry.of("FX", "", List.of(
                Posting.debit(alice, Money.of(1000, "AED")),
                Posting.credit(bob, Money.of(1000, "USD")))))
                .isInstanceOf(UnbalancedEntryException.class);

        assertThat(JournalEntry.of("FX", "", List.of(
                Posting.debit(alice, Money.of(1000, "AED")),
                Posting.credit(nostro, Money.of(1000, "AED")),
                Posting.debit(nostro, Money.of(272, "USD")),
                Posting.credit(bob, Money.of(272, "USD")))).postings()).hasSize(4);
    }

    @Test
    @DisplayName("reversal mirrors every leg and leaves the original alone")
    void reversal() {
        JournalEntry original = JournalEntry.transfer("TRF-1", "Rent", alice, bob, Money.of(1000, "AED"));
        JournalEntry reversed = original.reversal("REV-1");

        assertThat(reversed.postings().get(0).direction()).isEqualTo(Direction.CREDIT);
        assertThat(reversed.postings().get(1).direction()).isEqualTo(Direction.DEBIT);
        assertThat(reversed.narrative()).isEqualTo("Reversal of TRF-1");
        assertThat(original.postings().get(0).direction()).isEqualTo(Direction.DEBIT);
    }

    @Test
    @DisplayName("a transfer needs two different accounts")
    void noSelfTransfer() {
        assertThatThrownBy(() ->
                JournalEntry.transfer("X", "", alice, alice, Money.of(100, "AED")))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    @DisplayName("posting amounts are positive; the direction carries the sign")
    void positiveAmounts() {
        assertThatThrownBy(() -> Posting.debit(alice, Money.of(0, "AED")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Posting.debit(alice, Money.of(-1, "AED")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
