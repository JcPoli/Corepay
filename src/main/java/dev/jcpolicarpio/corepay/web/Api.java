package dev.jcpolicarpio.corepay.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request and response shapes, kept together so the HTTP contract can be read
 * in one place. Amounts are always minor units — the API never accepts a
 * decimal string, because "10.1" is not a number a ledger should guess at.
 */
public final class Api {

    private Api() {
    }

    @Schema(description = "Credentials for a demo user")
    public record TokenRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String scope) {
    }

    @Schema(description = "An internal transfer between two accounts on this ledger")
    public record TransferRequest(
            @NotBlank @Size(max = 34)
            @Schema(example = "AE070331234567890123456") String debtorAccountNumber,

            @NotBlank @Size(max = 34)
            @Schema(example = "AE070331234567890123457") String creditorAccountNumber,

            @Positive
            @Schema(description = "Amount in the currency's minor unit (fils, cents)", example = "125000")
            long amountMinor,

            @NotBlank @Pattern(regexp = "[A-Za-z]{3}")
            @Schema(example = "AED") String currency,

            @Size(max = 140)
            @Schema(description = "Free-text remittance information, as in ISO 20022 RmtInf",
                    example = "Invoice 2026-118") String remittanceInfo,

            @Size(max = 35)
            @Schema(description = "Client end-to-end identifier, as in ISO 20022 EndToEndId",
                    example = "E2E-2026-000118") String endToEndId) {
    }

    public record ReverseRequest(
            @Size(max = 240) String reason) {
    }

    public record OpenAccountRequest(
            @NotBlank @Size(max = 34) String accountNumber,
            @NotBlank @Size(max = 120) String holderName,
            @NotBlank @Pattern(regexp = "CUSTOMER|NOSTRO|SUSPENSE|INCOME") String type,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Schema(description = "Permitted overdraft in minor units; customer accounts only")
            long overdraftLimitMinor) {
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }

    @Schema(description = "A rule violation, not a server fault")
    public record ErrorResponse(
            String code,
            String message,
            String path) {
    }
}
