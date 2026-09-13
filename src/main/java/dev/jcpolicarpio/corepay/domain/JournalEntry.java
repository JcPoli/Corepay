package dev.jcpolicarpio.corepay.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A balanced set of postings — the only way value moves in this system.
 *
 * The invariant is enforced in the constructor, per currency: debits must
 * equal credits. An unbalanced entry cannot be constructed, so it can never
 * reach the database.
 */
public final class JournalEntry {

    private final String reference;
    private final String narrative;
    private final List<Posting> postings;

    private JournalEntry(String reference, String narrative, List<Posting> postings) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.narrative = narrative == null ? "" : narrative;
        this.postings = Collections.unmodifiableList(new ArrayList<>(postings));
    }

    public static JournalEntry of(String reference, String narrative, List<Posting> postings) {
        Objects.requireNonNull(postings, "postings");
        if (postings.size() < 2) {
            throw new UnbalancedEntryException(
                    "A journal entry needs at least two postings, got " + postings.size());
        }
        requireBalanced(postings);
        return new JournalEntry(reference, narrative, postings);
    }

    /** The canonical two-legged transfer: debit one account, credit another. */
    public static JournalEntry transfer(
            String reference, String narrative,
            java.util.UUID fromAccountId, java.util.UUID toAccountId, Money amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new UnbalancedEntryException("A transfer needs two different accounts");
        }
        return of(reference, narrative, List.of(
                Posting.debit(fromAccountId, amount),
                Posting.credit(toAccountId, amount)));
    }

    private static void requireBalanced(List<Posting> postings) {
        Map<String, Long> byCurrency = new HashMap<>();
        for (Posting posting : postings) {
            byCurrency.merge(
                    posting.amount().currency(),
                    posting.signedMinorUnits(),
                    Long::sum);
        }
        for (Map.Entry<String, Long> entry : byCurrency.entrySet()) {
            if (entry.getValue() != 0L) {
                throw new UnbalancedEntryException(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * The mirror image of this entry, used to reverse a posted payment.
     * Reversal is a new balanced entry, never an edit or a delete — the
     * original stays in the ledger for audit.
     */
    public JournalEntry reversal(String reversalReference) {
        List<Posting> flipped = new ArrayList<>(postings.size());
        for (Posting posting : postings) {
            flipped.add(new Posting(
                    posting.accountId(),
                    posting.direction().opposite(),
                    posting.amount()));
        }
        return new JournalEntry(reversalReference, "Reversal of " + reference, flipped);
    }

    public long totalDebitMinorUnits() {
        long total = 0L;
        for (Posting posting : postings) {
            if (posting.direction() == Direction.DEBIT) {
                total = Math.addExact(total, posting.amount().minorUnits());
            }
        }
        return total;
    }

    public String reference() {
        return reference;
    }

    public String narrative() {
        return narrative;
    }

    public List<Posting> postings() {
        return postings;
    }
}
