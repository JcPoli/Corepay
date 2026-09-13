package dev.jcpolicarpio.corepay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jcpolicarpio.corepay.AbstractIntegrationTest;
import dev.jcpolicarpio.corepay.domain.AccountType;
import dev.jcpolicarpio.corepay.domain.InsufficientFundsException;
import dev.jcpolicarpio.corepay.domain.PaymentStatus;
import dev.jcpolicarpio.corepay.persistence.AccountRepository;
import dev.jcpolicarpio.corepay.persistence.PostingRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The seeded demo accounts from V2 are the fixture for these tests. */
class PaymentServiceIT extends AbstractIntegrationTest {

    private static final String AMINA = "AE070331234567890123456";
    private static final String RAFAEL = "AE070331234567890123457";
    private static final String FROZEN = "AE070331234567890123458";

    @Autowired
    private PaymentService payments;

    @Autowired
    private AccountService accounts;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PostingRepository postingRepository;

    private TransferCommand transfer(long amountMinor) {
        return new TransferCommand(AMINA, RAFAEL, amountMinor, "AED", "Invoice 118", "E2E-1");
    }

    @Test
    @DisplayName("a transfer posts a balanced entry and moves both balances")
    void transferPosts() {
        long before = balanceOf(AMINA);
        PaymentResult result = payments.transfer(transfer(125000), UUID.randomUUID().toString()).result();

        assertThat(result.status()).isEqualTo(PaymentStatus.POSTED.name());
        assertThat(result.journalEntryId()).isNotNull();
        assertThat(balanceOf(AMINA)).isEqualTo(before - 125000);

        List<?> legs = postingRepository.findByJournalEntryIdOrderByIdAsc(result.journalEntryId());
        assertThat(legs).hasSize(2);
    }

    @Test
    @DisplayName("the same key with the same body replays and posts nothing new")
    void idempotentReplay() {
        String key = UUID.randomUUID().toString();
        PaymentService.TransferOutcome first = payments.transfer(transfer(5000), key);
        long postingsAfterFirst = postingRepository.count();

        PaymentService.TransferOutcome second = payments.transfer(transfer(5000), key);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.result().id()).isEqualTo(first.result().id());
        assertThat(postingRepository.count()).isEqualTo(postingsAfterFirst);
    }

    @Test
    @DisplayName("the same key with a different body is a conflict, never a duplicate")
    void idempotencyConflict() {
        String key = UUID.randomUUID().toString();
        payments.transfer(transfer(5000), key);

        assertThatThrownBy(() -> payments.transfer(transfer(6000), key))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("a customer cannot be overdrawn past its limit")
    void insufficientFunds() {
        assertThatThrownBy(() -> payments.transfer(
                new TransferCommand(AMINA, RAFAEL, 99_000_000L, "AED", null, null),
                UUID.randomUUID().toString()))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("a frozen account can receive but not send")
    void frozenAccount() {
        assertThat(payments.transfer(
                new TransferCommand(AMINA, FROZEN, 1000, "AED", null, null),
                UUID.randomUUID().toString()).result().status())
                .isEqualTo(PaymentStatus.POSTED.name());

        assertThatThrownBy(() -> payments.transfer(
                new TransferCommand(FROZEN, AMINA, 1000, "AED", null, null),
                UUID.randomUUID().toString()))
                .isInstanceOf(dev.jcpolicarpio.corepay.domain.AccountUnavailableException.class);
    }

    @Test
    @DisplayName("reversal mirrors the entry and leaves the original postings in place")
    void reversal() {
        long before = balanceOf(AMINA);
        PaymentResult posted = payments.transfer(transfer(20000), UUID.randomUUID().toString()).result();
        assertThat(balanceOf(AMINA)).isEqualTo(before - 20000);

        PaymentResult reversed = payments.reverse(posted.id(), "Duplicate instruction");

        assertThat(reversed.status()).isEqualTo(PaymentStatus.REVERSED.name());
        assertThat(reversed.reversalEntryId()).isNotNull();
        assertThat(balanceOf(AMINA)).isEqualTo(before);
        // The original entry is still there: four postings, not two rewritten.
        assertThat(postingRepository.findByJournalEntryIdOrderByIdAsc(posted.journalEntryId()))
                .hasSize(2);
        assertThat(postingRepository.findByJournalEntryIdOrderByIdAsc(reversed.reversalEntryId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("a settled payment cannot be rewound")
    void lifecycle() {
        PaymentResult posted = payments.transfer(transfer(1500), UUID.randomUUID().toString()).result();
        PaymentResult settled = payments.settle(posted.id());
        assertThat(settled.status()).isEqualTo(PaymentStatus.SETTLED.name());
        assertThatThrownBy(() -> payments.settle(posted.id()))
                .isInstanceOf(dev.jcpolicarpio.corepay.domain.InvalidTransitionException.class);
    }

    @Test
    @DisplayName("concurrent transfers out of one account cannot overdraw it")
    void concurrencyCannotOverdraw() {
        // Rafael has an overdraft limit of 50000; fund the test from Amina.
        String fresh = "TEST-CONC-" + UUID.randomUUID().toString().substring(0, 8);
        accounts.open(fresh, "Concurrency probe", AccountType.CUSTOMER, "AED", 0);
        payments.transfer(new TransferCommand(AMINA, fresh, 10_000, "AED", "seed", null),
                UUID.randomUUID().toString());

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        try {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    try {
                        payments.transfer(
                                new TransferCommand(fresh, RAFAEL, 2_000, "AED", null, null),
                                UUID.randomUUID().toString());
                        succeeded.incrementAndGet();
                    } catch (RuntimeException e) {
                        refused.incrementAndGet();
                    }
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
                future.get();
            }
        } catch (Exception e) {
            throw new AssertionError("Concurrency probe failed to run", e);
        } finally {
            pool.shutdownNow();
        }

        // Five transfers of 2000 fit in 10000; the rest must be refused, and
        // the balance must never go below zero.
        assertThat(succeeded.get()).isEqualTo(5);
        assertThat(refused.get()).isEqualTo(threads - 5);
        assertThat(balanceOf(fresh)).isZero();
    }

    private long balanceOf(String accountNumber) {
        UUID id = accountRepository.findByAccountNumber(accountNumber).orElseThrow().getId();
        return accounts.get(id).balanceMinor();
    }
}
