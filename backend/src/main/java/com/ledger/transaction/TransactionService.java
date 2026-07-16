package com.ledger.transaction;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.exchangerate.ExchangeRate;
import com.ledger.exchangerate.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Core transaction service handling transfers and reversals.
 *
 * Key design decisions:
 * - Uses pessimistic locking (SELECT FOR UPDATE) for concurrent safety
 * - Locks accounts in consistent order (lower ID first) to prevent deadlocks
 * - Idempotency via unique idempotency key on each transaction
 * - Transactions are immutable; reversals are new transactions referencing the original
 * - Exchange rate is captured at time of transfer and stored with the transaction
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              ExchangeRateRepository exchangeRateRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Executes a transfer between two accounts atomically.
     *
     * @param sourceAccountId  The account to debit
     * @param destAccountId    The account to credit
     * @param amount           The amount in source currency
     * @param idempotencyKey   Unique key to prevent duplicate transactions
     * @param authenticatedUsername The logged-in user (must own source account)
     * @return The created transaction
     */
    @Transactional
    public Transaction transfer(UUID sourceAccountId, UUID destAccountId,
                                BigDecimal amount, String idempotencyKey,
                                String authenticatedUsername) {

        // 1. Check idempotency — return existing transaction if key was already used
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate request detected for idempotency key: {}", idempotencyKey);
                return existing.get();
            }
        }

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (sourceAccountId.equals(destAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        // 2. Lock accounts in consistent order (lower ID first) to prevent deadlocks
        UUID firstId = sourceAccountId.compareTo(destAccountId) < 0 ? sourceAccountId : destAccountId;
        UUID secondId = sourceAccountId.compareTo(destAccountId) < 0 ? destAccountId : sourceAccountId;

        Account firstAccount = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstId));
        Account secondAccount = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondId));

        Account sourceAccount = sourceAccountId.equals(firstId) ? firstAccount : secondAccount;
        Account destAccount = destAccountId.equals(firstId) ? firstAccount : secondAccount;

        // 3. Verify ownership — user must own the source account
        if (!sourceAccount.getUser().getUsername().equals(authenticatedUsername)) {
            throw new SecurityException("You can only transfer from your own accounts");
        }

        // 4. Determine exchange rate
        BigDecimal exchangeRate;
        if (sourceAccount.getCurrency().equals(destAccount.getCurrency())) {
            exchangeRate = BigDecimal.ONE;
        } else {
            ExchangeRate rate = exchangeRateRepository
                    .findBySourceCurrencyAndTargetCurrency(sourceAccount.getCurrency(), destAccount.getCurrency())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No exchange rate found for " + sourceAccount.getCurrency() + " → " + destAccount.getCurrency()));
            exchangeRate = rate.getRate();
        }

        // 5. Calculate destination amount
        BigDecimal destAmount = amount.multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP);

        // 6. Check sufficient balance (reject if source balance would go negative)
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient balance. Available: " + sourceAccount.getBalance().toPlainString() +
                    " " + sourceAccount.getCurrency() + ", Required: " + amount.toPlainString());
        }

        // 7. Update balances atomically
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destAccount.setBalance(destAccount.getBalance().add(destAmount));

        // 8. Record the transaction
        Transaction txn = new Transaction();
        txn.setIdempotencyKey(idempotencyKey);
        txn.setSourceAccountId(sourceAccountId);
        txn.setDestAccountId(destAccountId);
        txn.setSourceAmount(amount);
        txn.setSourceCurrency(sourceAccount.getCurrency());
        txn.setDestAmount(destAmount);
        txn.setDestCurrency(destAccount.getCurrency());
        txn.setExchangeRate(exchangeRate);
        txn.setType(TransactionType.TRANSFER);
        txn.setReversed(false);

        Transaction saved = transactionRepository.save(txn);
        log.info("Transfer completed: {} {} {} → {} {} {} (rate: {})",
                amount, sourceAccount.getCurrency(), sourceAccountId,
                destAmount, destAccount.getCurrency(), destAccountId, exchangeRate);

        return saved;
    }

    /**
     * Reverses a transaction by creating a new REVERSAL transaction.
     * Uses the original transaction's exchange rate.
     * The original transaction remains immutable (only `reversed` flag is set).
     * Allows negative balances on reversal.
     *
     * @param transactionId The ID of the transaction to reverse
     * @param authenticatedUsername The logged-in user
     * @return The reversal transaction
     */
    @Transactional
    public Transaction reverse(UUID transactionId, String authenticatedUsername) {
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (original.isReversed()) {
            throw new IllegalStateException("Transaction has already been reversed");
        }

        if (original.getType() == TransactionType.REVERSAL) {
            throw new IllegalStateException("Cannot reverse a reversal transaction");
        }

        // Lock accounts in consistent order
        UUID firstId = original.getSourceAccountId().compareTo(original.getDestAccountId()) < 0 ? original.getSourceAccountId() : original.getDestAccountId();
        UUID secondId = original.getSourceAccountId().compareTo(original.getDestAccountId()) < 0 ? original.getDestAccountId() : original.getSourceAccountId();

        Account firstAccount = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstId));
        Account secondAccount = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondId));

        Account sourceAccount = original.getSourceAccountId().equals(firstId) ? firstAccount : secondAccount;
        Account destAccount = original.getDestAccountId().equals(firstId) ? firstAccount : secondAccount;

        // Verify ownership — user must own at least one of the accounts involved
        if (!sourceAccount.getUser().getUsername().equals(authenticatedUsername) &&
            !destAccount.getUser().getUsername().equals(authenticatedUsername)) {
            throw new SecurityException("You can only reverse transactions involving your own accounts");
        }

        // Reverse: credit source, debit destination (using original amounts and exchange rate)
        // Allow negative balance on reversal
        sourceAccount.setBalance(sourceAccount.getBalance().add(original.getSourceAmount()));
        destAccount.setBalance(destAccount.getBalance().subtract(original.getDestAmount()));

        // Create reversal transaction with original exchange rate
        Transaction reversal = new Transaction();
        reversal.setIdempotencyKey("REVERSAL-" + transactionId);
        reversal.setSourceAccountId(original.getDestAccountId());  // Reversed direction
        reversal.setDestAccountId(original.getSourceAccountId());
        reversal.setSourceAmount(original.getDestAmount());
        reversal.setSourceCurrency(original.getDestCurrency());
        reversal.setDestAmount(original.getSourceAmount());
        reversal.setDestCurrency(original.getSourceCurrency());
        reversal.setExchangeRate(original.getExchangeRate());  // Use original exchange rate
        reversal.setType(TransactionType.REVERSAL);
        reversal.setOriginalTransactionId(transactionId);
        reversal.setReversed(false);

        // Mark original as reversed (this is the only mutation — it's a flag, not a data change)
        original.setReversed(true);

        Transaction saved = transactionRepository.save(reversal);
        log.info("Reversal completed for transaction: {}", transactionId);

        return saved;
    }
}
