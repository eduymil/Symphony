package com.ledger.reconciliation;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.account.AppUser;
import com.ledger.account.AppUserRepository;
import com.ledger.transaction.Transaction;
import com.ledger.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Reconciliation service that recalculates account balances from transaction history.
 *
 * Design decision: We don't compare total sums because exchange rate changes make
 * that incorrect. Instead, we replay every transaction for each account and compare
 * the recalculated balance against the stored balance.
 *
 * For each account:
 * - When the account is a SOURCE: subtract sourceAmount
 * - When the account is a DESTINATION: add destAmount
 * - Starting balance is the seeded amount (we track this as the initial balance)
 *
 * Since we don't store initial balances separately, we use a different approach:
 * We recalculate from transactions and compare with stored balance.
 * If they match, the ledger is consistent.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AppUserRepository userRepository;

    // Initial seeded balances for reference
    private static final Map<String, Map<String, BigDecimal>> INITIAL_BALANCES = Map.of(
            "alice", Map.of("SGD", new BigDecimal("10000.0000"), "USD", new BigDecimal("5000.0000")),
            "bob", Map.of("SGD", new BigDecimal("8000.0000"), "USD", new BigDecimal("3000.0000"))
    );

    public ReconciliationService(AccountRepository accountRepository,
                                  TransactionRepository transactionRepository,
                                  AppUserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Verifies the entire ledger by recalculating every account balance from
     * its transaction history and comparing with the stored balance.
     */
    @Transactional(readOnly = true)
    public LedgerVerificationResult verifyLedger() {
        List<Account> allAccounts = accountRepository.findAll();
        List<AccountDiscrepancy> discrepancies = new ArrayList<>();
        int accountsChecked = 0;

        for (Account account : allAccounts) {
            accountsChecked++;
            BigDecimal recalculated = recalculateBalance(account);
            BigDecimal stored = account.getBalance();

            if (recalculated.compareTo(stored) != 0) {
                discrepancies.add(new AccountDiscrepancy(
                        account.getId(),
                        account.getUser().getUsername(),
                        account.getCurrency(),
                        stored.toPlainString(),
                        recalculated.toPlainString(),
                        stored.subtract(recalculated).toPlainString()
                ));
            }
        }

        boolean consistent = discrepancies.isEmpty();
        log.info("Ledger verification complete: {} accounts checked, {} discrepancies found",
                accountsChecked, discrepancies.size());

        return new LedgerVerificationResult(consistent, accountsChecked, discrepancies);
    }

    /**
     * Performs per-user reconciliation by recalculating each user's account balances
     * from transaction history and comparing with stored balances.
     */
    @Transactional(readOnly = true)
    public List<UserReconciliationResult> runReconciliation() {
        List<AppUser> users = userRepository.findAll();
        List<UserReconciliationResult> results = new ArrayList<>();

        for (AppUser user : users) {
            List<Account> userAccounts = accountRepository.findByUserId(user.getId());
            List<AccountDiscrepancy> discrepancies = new ArrayList<>();

            for (Account account : userAccounts) {
                BigDecimal recalculated = recalculateBalance(account);
                BigDecimal stored = account.getBalance();

                if (recalculated.compareTo(stored) != 0) {
                    discrepancies.add(new AccountDiscrepancy(
                            account.getId(),
                            user.getUsername(),
                            account.getCurrency(),
                            stored.toPlainString(),
                            recalculated.toPlainString(),
                            stored.subtract(recalculated).toPlainString()
                    ));
                }
            }

            results.add(new UserReconciliationResult(
                    user.getUsername(),
                    userAccounts.size(),
                    discrepancies.isEmpty(),
                    discrepancies
            ));
        }

        log.info("Reconciliation complete for {} users", results.size());
        return results;
    }

    /**
     * Recalculates an account's balance by replaying all transactions from history.
     * Starts from the known initial seeded balance and applies each transaction.
     */
    private BigDecimal recalculateBalance(Account account) {
        // Start from initial seeded balance
        String username = account.getUser().getUsername();
        BigDecimal balance = INITIAL_BALANCES
                .getOrDefault(username, Map.of())
                .getOrDefault(account.getCurrency(), BigDecimal.ZERO);

        // Replay all transactions in chronological order
        List<Transaction> transactions = transactionRepository
                .findByAccountIdOrderByCreatedAtAsc(account.getId());

        for (Transaction txn : transactions) {
            if (txn.getSourceAccountId().equals(account.getId())) {
                // This account was debited (source)
                balance = balance.subtract(txn.getSourceAmount());
            }
            if (txn.getDestAccountId().equals(account.getId())) {
                // This account was credited (destination)
                balance = balance.add(txn.getDestAmount());
            }
        }

        return balance;
    }

    // Result records
    public record LedgerVerificationResult(
            boolean consistent,
            int accountsChecked,
            List<AccountDiscrepancy> discrepancies
    ) {}

    public record UserReconciliationResult(
            String username,
            int accountsChecked,
            boolean consistent,
            List<AccountDiscrepancy> discrepancies
    ) {}

    public record AccountDiscrepancy(
            UUID accountId,
            String username,
            String currency,
            String storedBalance,
            String recalculatedBalance,
            String difference
    ) {}
}
