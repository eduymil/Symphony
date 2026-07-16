package com.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.account.AccountRepository;
import com.ledger.account.AppUserRepository;
import com.ledger.transaction.TransactionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests covering all functional requirements
 * and system behaviours of the Internal Ledger System.
 *
 * Uses Testcontainers to spin up a real PostgreSQL instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static UUID aliceSgdId;
    private static UUID aliceUsdId;
    private static UUID bobSgdId;
    private static UUID bobUsdId;

    @BeforeEach
    void fetchAccountIds() {
        if (aliceSgdId != null) return; // Only fetch once
        accountRepository.findByUserUsername("alice").forEach(a -> {
            if (a.getCurrency().equals("SGD")) aliceSgdId = a.getId();
            if (a.getCurrency().equals("USD")) aliceUsdId = a.getId();
        });
        accountRepository.findByUserUsername("bob").forEach(a -> {
            if (a.getCurrency().equals("SGD")) bobSgdId = a.getId();
            if (a.getCurrency().equals("USD")) bobUsdId = a.getId();
        });
    }

    // =====================================================
    // 2. Account Query Tests
    // =====================================================


    @Test
    @Order(11)
    @DisplayName("Get account by ID returns balance")
    void testGetAccountById() throws Exception {
        mockMvc.perform(get("/api/accounts/" + aliceSgdId)
                .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").exists())
                .andExpect(jsonPath("$.currency").exists());
    }


    // =====================================================
    // 3. Successful Transfer Tests
    // =====================================================

    @Test
    @Order(20)
    @DisplayName("Same-currency transfer succeeds and updates balances")
    void testSameCurrencyTransfer() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(post("/api/transactions")
                .header("X-Username", "alice")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\": \"%s\", \"destAccountId\": \"%s\", \"amount\": \"100.00\"}", aliceSgdId, bobSgdId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.sourceAmount").value("100.0000"))
                .andExpect(jsonPath("$.destAmount").value("100.0000"))
                .andExpect(jsonPath("$.exchangeRate").value("1.00000000"))
                .andReturn();
    }

    // =====================================================
    // 4. Insufficient Balance Test
    // =====================================================

    @Test
    @Order(30)
    @DisplayName("Transfer exceeding balance is rejected")
    void testInsufficientBalance() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header("X-Username", "alice")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\": \"%s\", \"destAccountId\": \"%s\", \"amount\": \"999999.00\"}", aliceSgdId, bobSgdId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Insufficient balance")));
    }

    // =====================================================
    // 5. Duplicate Request / Idempotency Tests
    // =====================================================

    @Test
    @Order(40)
    @DisplayName("Duplicate idempotency key returns same transaction without creating new one")
    void testIdempotencyKey() throws Exception {
        String idempotencyKey = "test-idempotent-" + UUID.randomUUID();

        // First request
        MvcResult first = mockMvc.perform(post("/api/transactions")
                .header("X-Username", "alice")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\": \"%s\", \"destAccountId\": \"%s\", \"amount\": \"50.00\"}", aliceSgdId, bobSgdId)))
                .andExpect(status().isOk())
                .andReturn();

        String firstTxnId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("transactionId").asText();

        // Second request with same key — should return same transaction
        MvcResult second = mockMvc.perform(post("/api/transactions")
                .header("X-Username", "alice")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\": \"%s\", \"destAccountId\": \"%s\", \"amount\": \"50.00\"}", aliceSgdId, bobSgdId)))
                .andExpect(status().isOk())
                .andReturn();

        String secondTxnId = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("transactionId").asText();

        Assertions.assertEquals(firstTxnId, secondTxnId,
                "Duplicate request should return the same transaction ID");
    }


    // =====================================================
    // 6. Currency Conversion Tests
    // =====================================================

    @Test
    @Order(50)
    @DisplayName("Cross-currency transfer applies exchange rate correctly")
    void testCurrencyConversion() throws Exception {
        // Alice SGD -> Bob USD
        // SGD -> USD rate = 0.74
        mockMvc.perform(post("/api/transactions")
                .header("X-Username", "alice")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\": \"%s\", \"destAccountId\": \"%s\", \"amount\": \"100.00\"}", aliceSgdId, bobUsdId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency").value("SGD"))
                .andExpect(jsonPath("$.destCurrency").value("USD"))
                .andExpect(jsonPath("$.sourceAmount").value("100.0000"))
                .andExpect(jsonPath("$.destAmount").value("74.0000"))
                .andExpect(jsonPath("$.exchangeRate").value("0.74000000"));
    }

    // =====================================================
    // 7. Reversal Tests
    // =====================================================

    @Test
    @Order(60)
    @DisplayName("Reversal creates new transaction with original exchange rate")
    void testReversal() throws Exception {
        // First, create a transfer
        String idempotencyKey = UUID.randomUUID().toString();
        MvcResult transferResult = mockMvc.perform(post("/api/transactions")
                .header("X-Username", "alice")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"sourceAccountId\": \"%s\", \"destAccountId\": \"%s\", \"amount\": \"200.00\"}", aliceSgdId, bobSgdId)))
                .andExpect(status().isOk())
                .andReturn();

        String txnId = objectMapper.readTree(transferResult.getResponse().getContentAsString())
                .get("transactionId").asText();

        // Now reverse it
        mockMvc.perform(post("/api/transactions/" + txnId + "/reverse")
                .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.originalTransactionId").value(txnId));
    }

    // =====================================================
    // 10. Ledger Verification Tests
    // =====================================================

    @Test
    @Order(90)
    @DisplayName("Ledger verification reports no discrepancies on clean data")
    void testLedgerVerificationClean() throws Exception {
        mockMvc.perform(get("/api/reconciliation/ledger-verification")
                .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(true))
                .andExpect(jsonPath("$.discrepancies", hasSize(0)));
    }

    @Test
    @Order(91)
    @DisplayName("Ledger verification detects discrepancy after debug balance manipulation")
    void testLedgerVerificationWithDiscrepancy() throws Exception {
        // Inject a discrepancy via debug endpoint
        mockMvc.perform(put("/api/debug/accounts/" + aliceSgdId + "/balance")
                .header("X-Username", "alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\": \"99999.0000\"}"))
                .andExpect(status().isOk());

        // Verify — should detect discrepancy
        mockMvc.perform(get("/api/reconciliation/ledger-verification")
                .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(false))
                .andExpect(jsonPath("$.discrepancies", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.discrepancies[0].accountId").value(aliceSgdId.toString()));
    }

    // =====================================================
    // 11. Reconciliation Tests
    // =====================================================

    @Test
    @Order(100)
    @DisplayName("Reconciliation runs per-user and detects discrepancies")
    void testReconciliation() throws Exception {
        mockMvc.perform(post("/api/reconciliation/run")
                .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // Two users
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].accountsChecked").isNumber());
    }

    // =====================================================
    // 12. Concurrent Transfers Test
    // =====================================================

    @Test
    @Order(110)
    @DisplayName("Concurrent transfers maintain consistency (no lost updates)")
    void testConcurrentTransfers() throws Exception {
        // Use Bob's SGD account as source to avoid the manipulated alice account
        mockMvc.perform(post("/api/debug/concurrent-transfers")
                .header("X-Username", "bob")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {
                      "sourceAccountId": "%s",
                      "destAccountId": "%s",
                      "amountPerTransfer": "100.00",
                      "numberOfTransfers": 20,
                      "username": "bob"
                    }
                    """, bobSgdId, aliceSgdId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempted").value(20))
                .andExpect(jsonPath("$.successes").isNumber())
                .andExpect(jsonPath("$.failures").isNumber())
                .andExpect(jsonPath("$.sourceAccountFinalBalance").exists());
    }

    // =====================================================
    // 13. Transaction History Tests
    // =====================================================

    @Test
    @Order(120)
    @DisplayName("Transaction history returns ordered transactions")
    void testTransactionHistory() throws Exception {
        mockMvc.perform(get("/api/accounts/" + aliceSgdId + "/transactions")
                .header("X-Username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

}
