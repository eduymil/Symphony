package com.ledger.transaction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction", indexes = {
    @Index(name = "idx_transaction_source", columnList = "source_account_id"),
    @Index(name = "idx_transaction_dest", columnList = "dest_account_id"),
    @Index(name = "idx_transaction_idempotency", columnList = "idempotency_key", unique = true)
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "dest_account_id", nullable = false)
    private UUID destAccountId;

    @Column(name = "source_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "dest_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal destAmount;

    @Column(name = "dest_currency", nullable = false, length = 3)
    private String destCurrency;

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "original_transaction_id")
    private UUID originalTransactionId;

    @Column(nullable = false)
    private boolean reversed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Transaction() {}

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public UUID getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public UUID getDestAccountId() { return destAccountId; }
    public void setDestAccountId(UUID destAccountId) { this.destAccountId = destAccountId; }

    public BigDecimal getSourceAmount() { return sourceAmount; }
    public void setSourceAmount(BigDecimal sourceAmount) { this.sourceAmount = sourceAmount; }

    public String getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(String sourceCurrency) { this.sourceCurrency = sourceCurrency; }

    public BigDecimal getDestAmount() { return destAmount; }
    public void setDestAmount(BigDecimal destAmount) { this.destAmount = destAmount; }

    public String getDestCurrency() { return destCurrency; }
    public void setDestCurrency(String destCurrency) { this.destCurrency = destCurrency; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public UUID getOriginalTransactionId() { return originalTransactionId; }
    public void setOriginalTransactionId(UUID originalTransactionId) { this.originalTransactionId = originalTransactionId; }

    public boolean isReversed() { return reversed; }
    public void setReversed(boolean reversed) { this.reversed = reversed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
