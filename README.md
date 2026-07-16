# Internal Ledger System

A full-stack financial ledger system that records account transactions and maintains accurate balances with multi-currency support.

**Tech Stack:** Spring Boot 3.3 (Java 21) · PostgreSQL 16 · React (Vite) · Docker Compose · Swagger/OpenAPI

---

## Table of Contents

- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Design Decisions](#design-decisions)
- [API Reference](#api-reference)
- [1. Running Tests in Terminal](#1-running-tests-in-terminal)
- [2. Performing Tests Through the Frontend](#2-performing-tests-through-the-frontend)
- [Debug Endpoints](#debug-endpoints)
- [Assumptions](#assumptions)
- [Design Decisions & Trade-offs](#design-decisions--trade-offs)

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Git (if cloning from GitHub)

### 1. Clone the Repository

```bash
git clone https://github.com/eduymil/Symphony.git
cd Symphony
```

### Run with Docker Compose (Recommended)

```bash
docker compose up --build
```

| Service    | URL                                  |
|------------|--------------------------------------|
| Frontend   | http://localhost:3000                 |
| Backend    | http://localhost:8080                 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:5433                        |


### Seeded Data

| User  | Password | SGD Account | USD Account |
|-------|----------|-------------|-------------|
| alice | password | 10,000 SGD  | 5,000 USD   |
| bob   | password | 8,000 SGD   | 3,000 USD   |

**Exchange Rates (seeded):**

| From | To  | Rate |
|------|-----|------|
| SGD  | USD | 0.74 |
| USD  | SGD | 1.35 |
| EUR  | SGD | 1.45 |
| SGD  | EUR | 0.69 |
| EUR  | USD | 1.08 |
| USD  | EUR | 0.93 |

---

## Architecture

```
┌─────────────┐     REST API      ┌──────────────────┐      JDBC       ┌────────────┐
│   React UI  │ ──────────────── │  Spring Boot 3   │ ─────────────── │ PostgreSQL │
│  (Vite)     │   X-Username     │                  │                  │            │
│  Port 3000  │   header auth    │  Port 8080       │   Pessimistic    │ Port 5433 (host)│
└─────────────┘                  │                  │   Locking        └────────────┘
                                  │  ┌────────────┐ │
                                  │  │ Auth Filter │ │
                                  │  ├────────────┤ │
                                  │  │ Transaction │ │ ← Atomic transfers, idempotency
                                  │  │  Service    │ │ ← Deadlock prevention (ordered locks)
                                  │  ├────────────┤ │
                                  │  │ Reconcilia- │ │ ← Balance recalculation from history
                                  │  │ tion Svc    │ │
                                  │  └────────────┘ │
                                  └──────────────────┘
```

### Domain Model

- **AppUser** — Simple user with username/password
- **Account** — Belongs to a user, has a currency and BigDecimal balance
- **Transaction** — Records both sides of a transfer with exchange rate, supports TRANSFER and REVERSAL types
- **ExchangeRate** — Currency pair with rate, modifiable via debug endpoints

---

## Design Decisions

### 1. Pessimistic Locking (SELECT FOR UPDATE)
Financial correctness is paramount. We use `@Lock(PESSIMISTIC_WRITE)` to acquire row-level locks on accounts during transfers. This prevents lost updates when multiple concurrent transfers target the same account.

### 2. Deadlock Prevention via Ordered Locking
When a transfer involves two accounts, we always lock the account with the **lower ID first**. This prevents deadlocks that would occur if two concurrent transactions lock accounts in different orders (e.g., A→B and B→A).

### 3. Exchange Rate Stored Per Transaction
Each transaction captures the exchange rate at the time of transfer. If exchange rates change later, historical transactions retain their original rate. This is critical for accurate reconciliation.

### 4. Balance Recalculation from History
For ledger verification and reconciliation, we don't compare total sums (which would be incorrect due to exchange rate changes). Instead, we replay every transaction from the initial seeded balance, applying each transaction's stored amounts to recalculate what the balance should be.

### 5. Idempotency via Database Unique Constraint
The `idempotency_key` column has a unique constraint. When a duplicate request arrives, we find the existing transaction by key and return it. This provides exactly-once semantics even under concurrent retries.

### 6. BigDecimal with Scale 4
All monetary amounts use `BigDecimal(19, 4)` to avoid floating-point precision issues. Exchange rates use `BigDecimal(19, 8)` for finer precision.

### 7. Header-Based Authentication
Simple `X-Username` header authentication. The auth filter validates the header on every request and sets the authenticated user in the request context.

---

## API Reference

Full interactive documentation available at **http://localhost:8080/swagger-ui.html**

### Authentication
| Method | Endpoint           | Description                    |
|--------|--------------------|--------------------------------|
| POST   | `/api/auth/login`  | Login with username/password   |
| POST   | `/api/auth/logout` | Logout                         |

### Accounts
| Method | Endpoint                         | Description                         |
|--------|----------------------------------|-------------------------------------|
| GET    | `/api/accounts/me`               | Get authenticated user's accounts   |
| GET    | `/api/accounts/{id}`             | Get account by ID                   |
| GET    | `/api/accounts/{id}/transactions`| Transaction history (by timestamp)  |
| GET    | `/api/accounts`                  | Get all accounts                    |

### Transactions
| Method | Endpoint                          | Headers Required      | Description              |
|--------|-----------------------------------|-----------------------|--------------------------|
| POST   | `/api/transactions`               | Idempotency-Key       | Create transfer          |
| POST   | `/api/transactions/{id}/reverse`  | —                     | Reverse a transaction    |

### Exchange Rates
| Method | Endpoint              | Description            |
|--------|-----------------------|------------------------|
| GET    | `/api/exchange-rates` | List all rates         |

### Reconciliation
| Method | Endpoint                              | Description                     |
|--------|---------------------------------------|---------------------------------|
| GET    | `/api/reconciliation/ledger-verification` | Verify entire ledger         |
| POST   | `/api/reconciliation/run`             | Run per-user reconciliation     |

### Debug (Testing Only)
| Method | Endpoint                            | Description                          |
|--------|-------------------------------------|--------------------------------------|
| PUT    | `/api/debug/accounts/{id}/balance`  | Directly set account balance         |
| PUT    | `/api/debug/exchange-rates/{id}`    | Modify exchange rate                 |
| POST   | `/api/debug/concurrent-transfers`   | Run concurrent transfer stress test  |

---

## 1. Running Tests in Terminal

### Prerequisites to run tests natively:
1. **Java 21+** installed on your machine (to run the Maven wrapper).
2. **Docker** running in the background (Testcontainers uses it to spin up a temporary PostgreSQL database).

To run the integration tests, open your terminal and run the appropriate command for your operating system:

**Mac / Linux:**
```bash
cd backend
./mvnw test
```

**Windows (Command Prompt / PowerShell):**
```cmd
cd backend
.\mvnw.cmd test
```

### Alternative: Run entirely in Docker (No Java required)
If you or your reviewer do not want to install Java on your host machine, you can run the test suite via a Dockerized Maven container. *(Note: Docker Desktop must still be running).*

*On Windows (PowerShell/WSL) or Mac:*
```bash
docker run --rm -v "${PWD}:/app" -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /app/backend maven:3.9-eclipse-temurin-21 mvn test
```

### Test Coverage

| Test | Requirement Covered |
|------|-------------------|
| `testSameCurrencyTransfer` | Record Transactions, Transfer Money |
| `testInsufficientBalance` | Balance validation |
| `testIdempotencyKey` | Duplicate prevention |
| `testCurrencyConversion` | Multi-currency transfers |
| `testReversal` | Transaction reversal |
| `testLedgerVerificationClean` / `testLedgerVerificationWithDiscrepancy` | Ledger verification |
| `testReconciliation` | Month-end reconciliation |
| `testConcurrentTransfers` | Concurrency safety |
| `testTransactionHistory` | Query balance (history) |
| `testGetAccountById` | Query balance (details) |

---

## 2. Performing Manual Tests

You can manually verify all functional requirements and system behaviors using either:
1. **The React Frontend:** Available at **http://localhost:3000** (Log in as `alice` / `password`).
2. **Swagger UI / OpenAPI:** Available at **http://localhost:8080/swagger-ui.html** (Use `alice` in the `X-Username` header where required).

*(Note: For the automated integration test suite, please refer to [Section 1](#1-running-tests-in-terminal) above).*

### Functional Requirements

#### 1. Record Transactions
- **Action:** Go to the **Transfer** page.
- **Steps:** Select one of your accounts as the source, select a destination account, and enter an amount. Click "Send Transfer".
- **Verification:** The transaction is recorded atomically and a confirmation message with the Transaction ID is displayed.

#### 2. Query Balance
- **Action:** Go to the **Dashboard** and **History** pages.
- **Steps:** On the Dashboard, view your current account balances. Click "History" to view the transaction history.
- **Verification:** Balances are correctly updated and history is ordered chronologically.

#### 3. Transfer Money
- **Action:** Go to the **Transfer** page.
- **Steps:** 
  1. Make a valid transfer. Check the Dashboard to verify your balance decreased and the destination balance increased.
  2. Try to transfer an amount greater than your balance.
- **Verification:** Valid transfers succeed. Insufficient balance transfers are rejected with a clear error message.

### System Behaviour

#### 4. Transaction Reversal
- **Action:** Go to the **History** page.
- **Steps:** Find an active transaction and click the red **Reverse** button.
- **Verification:** A new REVERSAL transaction is created, and the balances are updated accordingly. The original transaction is marked as reversed.

#### 5. Duplicate Transaction Prevention
- **Action:** Go to the **Transfer** page.
- **Steps:** The frontend automatically attaches a unique `Idempotency-Key` header to every transfer. The backend prevents duplicate processing if the same key is submitted twice (e.g. on network retries). 
- **Verification:** 
  1. Covered by backend tests, as the UI generates a new key per deliberate form submission to allow genuine repeat transfers
  2. Use [Swagger UI](http://localhost:8080/swagger-ui/index.html) and try to submit the same request twice with the same idempotency key

#### 6. Ledger Verification
- **Action:** Use the **Debug** and **Reconciliation** pages.
- **Steps:** 
  1. Go to **Debug** page -> **Set Account Balance** and change your balance manually (bypassing normal transfers).
  2. Go to **Reconciliation** page -> click **Run Verification**.
- **Verification:** The system will report a discrepancy because the current balance no longer matches the sum of historical transactions.

#### 7. Multi-Currency Transfers
- **Action:** Go to the **Transfer** page.
- **Steps:** Select a source account in one currency (e.g. Alice SGD) and a destination in another (e.g. Bob USD). 
- **Verification:** The UI will display the applicable exchange rate. Upon transferring, the source decreases by the entered amount, and the destination increases by the correctly converted amount.

#### 8. Concurrency Safety
- **Action:** Go to the **Debug** page.
- **Steps:** Scroll to **Concurrent Transfers Test**. Enter an amount and a number of transfers (e.g. 10). Click **Run Concurrent Test**.
- **Verification:** The system spawns multiple threads to transfer money simultaneously from the same account. Pessimistic locking ensures no updates are lost and balances are strictly consistent.

#### 9. Month-End Reconciliation
- **Action:** Go to the **Reconciliation** page.
- **Steps:** Click **Run Reconciliation**.
- **Verification:** The system rebuilds the entire ledger from the transaction history and verifies all current balances against the recalculated totals.

---

## Debug Endpoints

> ⚠️ **These endpoints exist solely for testing and demonstration purposes.**

| Endpoint | Purpose | How to Test |
|----------|---------|-------------|
| `PUT /api/debug/accounts/{id}/balance` | Set balance directly (creates discrepancy) | Set balance → Run ledger verification |
| `PUT /api/debug/exchange-rates/{id}` | Modify exchange rate | Change rate → Make transfer → Verify new rate is used |
| `POST /api/debug/concurrent-transfers` | Stress test concurrent transfers | Run with 10+ threads → Check no lost updates |


---

## Assumptions

1. **Self-transfers allowed** — Users can transfer between their own accounts (e.g., Alice SGD → Alice USD for currency conversion)
2. **Initial balances are known** — Reconciliation starts from hardcoded initial balances when replaying transactions
3. **Negative balance on reversal** — Reversals can cause negative balances, but normal transfers cannot
4. **No partial transfers** — A transfer either fully succeeds or fully fails
5. **Simple authentication** — Purpose of this application is to demostrate core function. This is not for production.
6. **Exchange rates are not dynamically fetched** — They are seeded and can only be changed via debug endpoints

---

## Design Decisions & Trade-offs

This application intentionally takes a simplified approach in certain areas to focus on demonstrating core financial ledger concepts (like consistency, idempotency, and concurrency). Below are the key design decisions and trade-offs:

### 1. Concurrency Management
- **Decision:** Used pessimistic locking (`SELECT ... FOR UPDATE`) with ordered lock acquisition.
- **Benefit:** Guarantees strong consistency and prevents "lost updates" or race conditions when two transactions happen simultaneously.
- **Trade-off:** Reduces overall system throughput under extremely high contention. In a high-throughput, highly distributed production scenario, optimistic locking or distributed messaging queues (e.g., Kafka) might be considered instead.
- **Testing & Validation:** Concurrency is rigorously validated through an aggressive multi-threaded stress test endpoint. Because the synchronization relies entirely on PostgreSQL's row-level locks (rather than application-level JVM synchronization), this architecture inherently protects against race conditions even in a fully distributed, multi-instance production environment.

### 2. Reconciliation & Exchange Rates
- **Decision:** Reconciliation recalculates balances by replaying each individual transfer chronologically from a known initial seeded balance, rather than simply summing up the net worth of each account.
- **Benefit:** This ensures reconciliation is always perfectly accurate even when exchange rates change over time. Every historical transaction permanently locks in the exchange rate that was active at the time of transfer.
- **Trade-off:** Hardcoding initial balances simplifies this demonstration, but a real production system would require a dynamic ledger state snapshot/migration system to handle organic account creation.
- **Future Proofing:** Exchange rates are currently seeded and updated via a debug API, but the architecture is designed so it can easily scale to dynamically fetch real-time rates from external APIs without breaking past transaction histories.

### 3. Security & Authentication
- **Decision:** Used a simplified header-based authentication filter.
- **Benefit:** Extremely simple to develop and test without the overhead of managing session tokens or JWTs.
- **Trade-off:** This app does not take security seriously as it is intended solely to demonstrate the transaction logic. In a real production system, strict security designs would be mandated—including proper OAuth/JWT authentication, session management, and robust data privacy (for example, other users' account balances would never be exposed in UI dropdowns).

### 4. Database Schema
- **Decision:** Relying on Hibernate's `ddl-auto=update`.
- **Benefit:** No migration tool is required, accelerating development and setup.
- **Trade-off:** Not suitable for safe schema evolution in a live production environment. A real system would strictly rely on migration tools like Flyway or Liquibase.
