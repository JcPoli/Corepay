package dev.jcpolicarpio.corepay.service;

import dev.jcpolicarpio.corepay.domain.AccountStatus;
import dev.jcpolicarpio.corepay.domain.AccountType;
import dev.jcpolicarpio.corepay.domain.AccountUnavailableException;
import dev.jcpolicarpio.corepay.domain.Direction;
import dev.jcpolicarpio.corepay.domain.JournalEntry;
import dev.jcpolicarpio.corepay.domain.Ledger;
import dev.jcpolicarpio.corepay.domain.Money;
import dev.jcpolicarpio.corepay.domain.Posting;
import dev.jcpolicarpio.corepay.persistence.AccountEntity;
import dev.jcpolicarpio.corepay.persistence.AccountRepository;
import dev.jcpolicarpio.corepay.persistence.JournalEntryEntity;
import dev.jcpolicarpio.corepay.persistence.JournalEntryRepository;
import dev.jcpolicarpio.corepay.persistence.PostingEntity;
import dev.jcpolicarpio.corepay.persistence.PostingRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only component allowed to write postings.
 *
 * It takes a balanced domain JournalEntry — the balance invariant is enforced
 * in the constructor, so an unbalanced entry cannot reach here — locks the
 * affected accounts in a deterministic order, re-checks funds and account
 * status against the freshly locked state, then appends.
 */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final JournalEntryRepository journalEntries;
    private final PostingRepository postings;

    public LedgerService(AccountRepository accounts,
                         JournalEntryRepository journalEntries,
                         PostingRepository postings) {
        this.accounts = accounts;
        this.journalEntries = journalEntries;
        this.postings = postings;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID post(JournalEntry entry) {
        Set<UUID> touched = new LinkedHashSet<>();
        for (Posting posting : entry.postings()) {
            touched.add(posting.accountId());
        }
        // Sorted so concurrent transfers between the same two accounts always
        // take their locks in the same order and cannot deadlock.
        List<UUID> ordered = touched.stream().sorted().collect(Collectors.toList());
        List<AccountEntity> locked = accounts.lockAll(ordered);
        if (locked.size() != ordered.size()) {
            throw new AccountNotFoundException("One or more accounts in the entry do not exist");
        }
        Map<UUID, AccountEntity> byId = locked.stream()
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));

        for (Posting posting : entry.postings()) {
            AccountEntity account = byId.get(posting.accountId());
            requireUsable(account, posting.direction());
            requireCurrencyMatches(account, posting);
            if (posting.direction() == Direction.DEBIT) {
                Money available = balanceOf(account);
                Ledger.requireSufficientFunds(
                        account.getType(), available, posting.amount(), account.getOverdraftLimitMinor());
            }
        }

        JournalEntryEntity saved = journalEntries.save(
                new JournalEntryEntity(UUID.randomUUID(), entry.reference(), entry.narrative()));

        List<PostingEntity> rows = new ArrayList<>(entry.postings().size());
        for (Posting posting : entry.postings()) {
            rows.add(new PostingEntity(
                    saved.getId(),
                    posting.accountId(),
                    posting.direction(),
                    posting.amount().minorUnits(),
                    posting.amount().currency()));
        }
        postings.saveAll(rows);
        return saved.getId();
    }

    /**
     * Balance derived from postings. The sign rule lives in AccountType, not
     * in SQL, so there is exactly one definition of which side increases an
     * account.
     */
    @Transactional(readOnly = true)
    public Money balanceOf(AccountEntity account) {
        long debitsMinusCredits = postings.debitsMinusCredits(account.getId());
        long signed = account.getType().normalSide() == Direction.DEBIT
                ? debitsMinusCredits
                : -debitsMinusCredits;
        return Money.of(signed, account.getCurrency());
    }

    private void requireUsable(AccountEntity account, Direction direction) {
        AccountStatus status = account.getStatus();
        boolean allowed = direction == Direction.DEBIT ? status.canDebit() : status.canCredit();
        if (!allowed) {
            throw new AccountUnavailableException(account.getAccountNumber(), status, direction);
        }
    }

    private void requireCurrencyMatches(AccountEntity account, Posting posting) {
        if (!account.getCurrency().equalsIgnoreCase(posting.amount().currency())) {
            throw new dev.jcpolicarpio.corepay.domain.CurrencyMismatchException(
                    account.getCurrency(), posting.amount().currency());
        }
    }

    /** Account types that may legitimately hold a negative balance. */
    public static boolean mayGoNegative(AccountType type) {
        return type != AccountType.CUSTOMER;
    }
}
