package com.ledger.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Query account balances and transaction history")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    @Operation(
        summary = "Get all accounts for authenticated user",
        description = "Returns all accounts belonging to the currently logged-in user with their balances.",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of user's accounts")
        }
    )
    public ResponseEntity<List<AccountDto>> getMyAccounts(HttpServletRequest request) {
        AppUser user = (AppUser) request.getAttribute("authenticatedUser");
        List<AccountDto> accounts = accountService.getAccountsByUsername(user.getUsername());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get account by ID",
        description = "Returns the account details including current balance.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Account details"),
            @ApiResponse(responseCode = "404", description = "Account not found")
        }
    )
    public ResponseEntity<?> getAccount(
            @Parameter(description = "Account ID") @PathVariable UUID id) {
        return accountService.getAccountById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/transactions")
    @Operation(
        summary = "Get transaction history for account",
        description = "Returns all transactions for the given account, ordered by timestamp (newest first).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Transaction history"),
            @ApiResponse(responseCode = "404", description = "Account not found")
        }
    )
    public ResponseEntity<?> getTransactionHistory(
            @Parameter(description = "Account ID") @PathVariable UUID id) {
        if (accountService.getAccountById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(accountService.getTransactionHistory(id));
    }

    @GetMapping
    @Operation(
        summary = "Get all accounts",
        description = "Returns all accounts in the system. Useful for selecting transfer destinations.",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of all accounts")
        }
    )
    public ResponseEntity<List<AccountDto>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    public record AccountDto(UUID id, String username, String currency, String balance) {}
}
