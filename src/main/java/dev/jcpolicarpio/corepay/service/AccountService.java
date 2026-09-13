package dev.jcpolicarpio.corepay.service;

import dev.jcpolicarpio.corepay.domain.AccountStatus;
import dev.jcpolicarpio.corepay.domain.AccountType;
import dev.jcpolicarpio.corepay.domain.Money;
import dev.jcpolicarpio.corepay.persistence.AccountEntity;
import dev.jcpolicarpio.corepay.persistence.AccountRepository;
import dev.jcpolicarpio.corepay.persistence.PostingEntity;
import dev.jcpolicarpio.corepay.persistence.PostingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final PostingRepository postings;
    private final LedgerService ledger;

    public AccountService(AccountRepository accounts, PostingRepository postings, LedgerService ledger) {
        this.accounts = accounts;
        this.postings = postings;
        this.ledger = ledger;
    }

    public record AccountView(
            UUID id,
            String accountNumber,
            String holderName,
            String type,
            String status,
            String currency,
            long balanceMinor,
            long overdraftLimitMinor,
            long postingCount) {
    }

    public record StatementLine(
            long id,
            String direction,
            long amountMinor,
            String currency,
            long runningBalanceMinor,
            String postedAt,
            UUID journalEntryId) {
    }

    @Transactional(readOnly = true)
    public List<AccountView> list() {
        List<AccountView> views = new ArrayList<>();
        for (AccountEntity account : accounts.findAll()) {
            views.add(toView(account));
        }
        views.sort((a, b) -> a.accountNumber().compareTo(b.accountNumber()));
        return views;
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID id) {
        return toView(accounts.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("No account with id " + id)));
    }

    @Transactional
    public AccountView open(String accountNumber, String holderName, AccountType type,
                            String currency, long overdraftLimitMinor) {
        if (accounts.findByAccountNumber(accountNumber).isPresent()) {
            throw new InvalidPaymentException("Account " + accountNumber + " already exists");
        }
        AccountEntity saved = accounts.save(new AccountEntity(
                UUID.randomUUID(), accountNumber, holderName, type,
                AccountStatus.ACTIVE, currency.toUpperCase(), Math.max(0L, overdraftLimitMinor)));
        return toView(saved);
    }

    /**
     * Statement with a running balance. The running figure is rebuilt from the
     * account's full posting history rather than stored, so it can never drift
     * from the ledger.
     */
    @Transactional(readOnly = true)
    public List<StatementLine> statement(UUID accountId, int page, int size) {
        AccountEntity account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("No account with id " + accountId));

        long running = ledger.balanceOf(account).minorUnits();
        int skipped = page * size;

        // The newest posting sits at the current balance. Rows newer than this
        // page are unwound first so the running figure is correct for a page
        // deep in the history — the balance is never stored, only derived.
        if (skipped > 0) {
            for (PostingEntity newer : postings.findStatement(accountId, PageRequest.of(0, skipped))) {
                running -= signedEffect(account.getType(), newer);
            }
        }

        List<StatementLine> lines = new ArrayList<>();
        for (PostingEntity posting : postings.findStatement(accountId, PageRequest.of(page, size))) {
            lines.add(new StatementLine(
                    posting.getId(),
                    posting.getDirection().name(),
                    posting.getAmountMinor(),
                    posting.getCurrency(),
                    running,
                    posting.getCreatedAt() == null ? "" : posting.getCreatedAt().toString(),
                    posting.getJournalEntryId()));
            running -= signedEffect(account.getType(), posting);
        }
        return lines;
    }

    private long signedEffect(AccountType type, PostingEntity posting) {
        return type.signOf(posting.getDirection()) * posting.getAmountMinor();
    }

    private AccountView toView(AccountEntity account) {
        Money balance = ledger.balanceOf(account);
        return new AccountView(
                account.getId(),
                account.getAccountNumber(),
                account.getHolderName(),
                account.getType().name(),
                account.getStatus().name(),
                account.getCurrency(),
                balance.minorUnits(),
                account.getOverdraftLimitMinor(),
                postings.countByAccountId(account.getId()));
    }
}
