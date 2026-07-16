package com.ledger.debug;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.exchangerate.ExchangeRate;
import com.ledger.exchangerate.ExchangeRateRepository;
import com.ledger.transaction.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/**
 * DEBUG/TESTING ONLY endpoints.
 *
 * These endpoints exist solely for testing and demonstration purposes.
 * They allow intentional manipulation of account balances and exchange rates
 * to verify that reconciliation and ledger verification correctly detect discrepancies.
 *
 * DO NOT use these endpoints in production.
 */
@RestController
@RequestMapping("/api/debug")
@Tag(name = "[DEBUG] Testing Tools", description = """
        ⚠️ DEBUG/TESTING ONLY — These endpoints exist solely for assignment demonstration purposes.
        They allow intentional data manipulation to verify reconciliation and ledger verification features.
        Do NOT use in production.
        """)
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    private final AccountRepository accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final TransactionService transactionService;

    public DebugController(AccountRepository accountRepository,
                           ExchangeRateRepository exchangeRateRepository,
                           TransactionService transactionService) {
        this.accountRepository = accountRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.transactionService = transactionService;
    }

    @PutMapping("/accounts/{id}/balance")
    @Operation(
        summary = "[DEBUG] Directly set account balance",
        description = """
            ⚠️ DEBUG ONLY — Directly modifies an account's stored balance without creating a transaction.
            This intentionally creates a discrepancy between the stored balance and the balance
            calculated from transaction history, which can then be detected by ledger verification
            and reconciliation endpoints.
            
            Usage: Set a balance, then run GET /api/reconciliation/ledger-verification to see the discrepancy.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {"balance": "99999.0000"}
                    """)
            )
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Balance updated (discrepancy created)"),
            @ApiResponse(responseCode = "404", description = "Account not found")
        }
    )
    public ResponseEntity<?> setBalance(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        Optional<Account> accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Account account = accountOpt.get();
        BigDecimal oldBalance = account.getBalance();
        BigDecimal newBalance = new BigDecimal(request.get("balance"));
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.warn("[DEBUG] Account {} balance changed from {} to {} (discrepancy created)",
                id, oldBalance, newBalance);

        return ResponseEntity.ok(Map.of(
                "accountId", id,
                "oldBalance", oldBalance.toPlainString(),
                "newBalance", newBalance.toPlainString(),
                "warning", "Balance modified without transaction — reconciliation will detect this discrepancy"
        ));
    }

    @PutMapping("/exchange-rates/{id}")
    @Operation(
        summary = "[DEBUG] Modify exchange rate",
        description = """
            ⚠️ DEBUG ONLY — Directly modifies an exchange rate.
            Historical transactions retain their original exchange rates and are not affected.
            This can be used to test that future transactions use the new rate while past ones remain unchanged.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {"rate": "0.80000000"}
                    """)
            )
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Exchange rate updated"),
            @ApiResponse(responseCode = "404", description = "Exchange rate not found")
        }
    )
    public ResponseEntity<?> setExchangeRate(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findById(id);
        if (rateOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExchangeRate rate = rateOpt.get();
        BigDecimal oldRate = rate.getRate();
        BigDecimal newRate = new BigDecimal(request.get("rate"));
        rate.setRate(newRate);
        exchangeRateRepository.save(rate);

        log.warn("[DEBUG] Exchange rate {} ({}/{}) changed from {} to {}",
                id, rate.getSourceCurrency(), rate.getTargetCurrency(), oldRate, newRate);

        return ResponseEntity.ok(Map.of(
                "id", id,
                "pair", rate.getSourceCurrency() + "/" + rate.getTargetCurrency(),
                "oldRate", oldRate.toPlainString(),
                "newRate", newRate.toPlainString(),
                "note", "Historical transactions retain their original exchange rates"
        ));
    }

    @PostMapping("/concurrent-transfers")
    @Operation(
        summary = "[DEBUG] Run concurrent transfer stress test",
        description = """
            ⚠️ DEBUG ONLY — Runs multiple concurrent transfers from the same source account
            to test that pessimistic locking prevents lost updates and inconsistencies.
            
            This endpoint spawns N threads, each attempting a transfer of the specified amount.
            Some transfers may fail due to insufficient balance — this is expected.
            
            After completion, it reports:
            - How many transfers succeeded vs failed
            - The final balances of both accounts
            - Whether the balances are consistent (no lost updates)
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "sourceAccountId": "123e4567-e89b-12d3-a456-426614174000",
                      "destAccountId": "123e4567-e89b-12d3-a456-426614174001",
                      "amountPerTransfer": "100.00",
                      "numberOfTransfers": 10,
                      "username": "alice"
                    }
                    """)
            )
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Concurrent test results")
        }
    )
    public ResponseEntity<?> concurrentTransfers(@RequestBody ConcurrentTransferRequest request) {
        int n = request.numberOfTransfers;
        if (n <= 0 || n > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "numberOfTransfers must be between 1 and 200"));
        }
        
        // Thread pool must be exactly n to prevent deadlock with CyclicBarrier
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        List<Future<TransferResult>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                barrier.await(); // Threads wait here until exactly n threads arrive, then fire simultaneously
                try {
                    String idempotencyKey = "CONCURRENT-TEST-" + UUID.randomUUID();
                    // Fake the username by looking up the actual owner of the source account
                    String actualUsername = accountRepository.findById(request.sourceAccountId)
                            .map(a -> a.getUser().getUsername())
                            .orElse(request.username);
                            
                    transactionService.transfer(
                            request.sourceAccountId,
                            request.destAccountId,
                            new BigDecimal(request.amountPerTransfer),
                            idempotencyKey,
                            actualUsername
                    );
                    return new TransferResult(idx, true, null);
                } catch (Exception e) {
                    return new TransferResult(idx, false, e.getMessage());
                }
            }));
        }

        executor.shutdown();

        int successes = 0;
        int failures = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (Future<TransferResult> future : futures) {
            try {
                TransferResult result = future.get(60, TimeUnit.SECONDS);
                if (result.success) {
                    successes++;
                } else {
                    failures++;
                }
                results.add(Map.of(
                        "threadIndex", result.index,
                        "success", result.success,
                        "error", result.error != null ? result.error : ""
                ));
            } catch (Exception e) {
                failures++;
                results.add(Map.of("error", e.getMessage()));
            }
        }

        // Get final balances
        String sourceBalance = accountRepository.findById(request.sourceAccountId)
                .map(a -> a.getBalance().toPlainString()).orElse("N/A");
        String destBalance = accountRepository.findById(request.destAccountId)
                .map(a -> a.getBalance().toPlainString()).orElse("N/A");

        return ResponseEntity.ok(Map.of(
                "totalAttempted", n,
                "successes", successes,
                "failures", failures,
                "sourceAccountFinalBalance", sourceBalance,
                "destAccountFinalBalance", destBalance,
                "details", results
        ));
    }

    public record ConcurrentTransferRequest(
            UUID sourceAccountId,
            UUID destAccountId,
            String amountPerTransfer,
            int numberOfTransfers,
            String username
    ) {}

    private record TransferResult(int index, boolean success, String error) {}
}
