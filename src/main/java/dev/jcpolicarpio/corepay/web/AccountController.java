package dev.jcpolicarpio.corepay.web;

import dev.jcpolicarpio.corepay.domain.AccountType;
import dev.jcpolicarpio.corepay.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    @Operation(summary = "List accounts with balances derived from the ledger")
    public List<AccountService.AccountView> list() {
        return accounts.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one account")
    public AccountService.AccountView get(@PathVariable UUID id) {
        return accounts.get(id);
    }

    @PostMapping
    @Operation(summary = "Open an account")
    public ResponseEntity<AccountService.AccountView> open(
            @Valid @RequestBody Api.OpenAccountRequest request) {
        AccountService.AccountView view = accounts.open(
                request.accountNumber(),
                request.holderName(),
                AccountType.valueOf(request.type()),
                request.currency(),
                request.overdraftLimitMinor());
        return ResponseEntity.status(201).body(view);
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Statement with a running balance, newest first")
    public List<AccountService.StatementLine> statement(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return accounts.statement(id, Math.max(0, page), Math.min(Math.max(1, size), 200));
    }
}
