package com.ledger.transaction;

import com.ledger.account.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Record transfers and reversals")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(
        summary = "Create a new transfer",
        description = """
            Transfers money from source account to destination account.
            Requires `Idempotency-Key` header to prevent duplicate transactions.
            Source account must belong to the authenticated user.
            If currencies differ, exchange rate is applied automatically.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "sourceAccountId": "123e4567-e89b-12d3-a456-426614174000",
                      "destAccountId": "123e4567-e89b-12d3-a456-426614174001",
                      "amount": "100.00"
                    }
                    """)
            )
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Transfer successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request (insufficient balance, invalid accounts, etc.)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Idempotency key missing")
        }
    )
    public ResponseEntity<?> createTransfer(
            @RequestBody TransferRequest request,
            @Parameter(description = "Unique key to prevent duplicate transactions", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Idempotency-Key header is required"));
        }

        AppUser user = (AppUser) httpRequest.getAttribute("authenticatedUser");

        try {
            Transaction txn = transactionService.transfer(
                    request.sourceAccountId(),
                    request.destAccountId(),
                    new BigDecimal(request.amount()),
                    idempotencyKey,
                    user.getUsername()
            );
            return ResponseEntity.ok(toResponse(txn));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reverse")
    @Operation(
        summary = "Reverse a transaction",
        description = """
            Creates a new reversal transaction that references the original.
            Uses the original transaction's exchange rate.
            Original transaction remains immutable.
            Allows negative balance if reversal causes it.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Reversal successful"),
            @ApiResponse(responseCode = "400", description = "Transaction already reversed or not found")
        }
    )
    public ResponseEntity<?> reverseTransaction(
            @Parameter(description = "Transaction ID to reverse") @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        AppUser user = (AppUser) httpRequest.getAttribute("authenticatedUser");

        try {
            Transaction reversal = transactionService.reverse(id, user.getUsername());
            return ResponseEntity.ok(toResponse(reversal));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toResponse(Transaction txn) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("transactionId", txn.getId().toString());
        response.put("type", txn.getType().name());
        response.put("sourceAccountId", txn.getSourceAccountId());
        response.put("destAccountId", txn.getDestAccountId());
        response.put("sourceAmount", txn.getSourceAmount().setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
        response.put("sourceCurrency", txn.getSourceCurrency());
        response.put("destAmount", txn.getDestAmount().setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
        response.put("destCurrency", txn.getDestCurrency());
        response.put("exchangeRate", txn.getExchangeRate().setScale(8, java.math.RoundingMode.HALF_UP).toPlainString());
        response.put("reversed", txn.isReversed());
        response.put("originalTransactionId", txn.getOriginalTransactionId() != null ? txn.getOriginalTransactionId().toString() : null);
        response.put("createdAt", txn.getCreatedAt().toString());
        return response;
    }

    public record TransferRequest(UUID sourceAccountId, UUID destAccountId, String amount) {}
}
