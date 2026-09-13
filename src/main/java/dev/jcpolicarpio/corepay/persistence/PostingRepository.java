package dev.jcpolicarpio.corepay.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<PostingEntity, Long> {

    /**
     * Debits minus credits for one account, in minor units.
     *
     * The database deliberately does not know which side is "positive" for a
     * given account type — that rule lives in AccountType so it exists in
     * exactly one place. The service applies the sign.
     */
    @Query("""
            select coalesce(sum(case when p.direction = dev.jcpolicarpio.corepay.domain.Direction.DEBIT
                                     then p.amountMinor else -p.amountMinor end), 0)
            from PostingEntity p
            where p.accountId = :accountId
            """)
    long debitsMinusCredits(@Param("accountId") UUID accountId);

    @Query("select p from PostingEntity p where p.accountId = :accountId order by p.id desc")
    List<PostingEntity> findStatement(@Param("accountId") UUID accountId, Pageable pageable);

    long countByAccountId(UUID accountId);

    List<PostingEntity> findByJournalEntryIdOrderByIdAsc(UUID journalEntryId);
}
