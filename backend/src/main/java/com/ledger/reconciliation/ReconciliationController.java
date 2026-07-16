package com.ledger.reconciliation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reconciliation")
@Tag(name = "Reconciliation", description = "Ledger verification and month-end reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/ledger-verification")
    @Operation(
        summary = "Verify entire ledger integrity",
        description = """
            Recalculates every account balance from its complete transaction history
            (including reversals and historical exchange rates) and compares it against
            the stored balance. Reports any discrepancies found.
            
            This does NOT simply compare total balances — it replays all transactions
            from the initial seeded balance to ensure correctness.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Verification results")
        }
    )
    public ResponseEntity<ReconciliationService.LedgerVerificationResult> verifyLedger() {
        return ResponseEntity.ok(reconciliationService.verifyLedger());
    }

    @PostMapping("/run")
    @Operation(
        summary = "Run month-end reconciliation",
        description = """
            Performs per-user reconciliation by recalculating each user's account balances
            from transaction history (including reversals and historical exchange rates)
            and comparing them with stored balances.
            
            This endpoint replaces scheduled month-end reconciliation —
            call it manually or via automation as needed.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Reconciliation results per user")
        }
    )
    public ResponseEntity<?> runReconciliation() {
        return ResponseEntity.ok(reconciliationService.runReconciliation());
    }
}
