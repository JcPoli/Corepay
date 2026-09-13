package dev.jcpolicarpio.corepay.persistence;

import dev.jcpolicarpio.corepay.domain.AccountStatus;
import dev.jcpolicarpio.corepay.domain.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "overdraft_limit_minor", nullable = false)
    private long overdraftLimitMinor;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AccountEntity() {
    }

    public AccountEntity(UUID id, String accountNumber, String holderName, AccountType type,
                         AccountStatus status, String currency, long overdraftLimitMinor) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.type = type;
        this.status = status;
        this.currency = currency;
        this.overdraftLimitMinor = overdraftLimitMinor;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public AccountType getType() {
        return type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getOverdraftLimitMinor() {
        return overdraftLimitMinor;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
