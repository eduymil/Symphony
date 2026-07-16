package com.ledger.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId = :accountId OR t.destAccountId = :accountId ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountId(@Param("accountId") UUID accountId);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId = :accountId OR t.destAccountId = :accountId ORDER BY t.createdAt ASC")
    List<Transaction> findByAccountIdOrderByCreatedAtAsc(@Param("accountId") UUID accountId);

    List<Transaction> findByOriginalTransactionId(UUID originalTransactionId);
}
