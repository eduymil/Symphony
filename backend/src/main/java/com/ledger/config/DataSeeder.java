package com.ledger.config;

import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.account.AppUser;
import com.ledger.account.AppUserRepository;
import com.ledger.exchangerate.ExchangeRate;
import com.ledger.exchangerate.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds initial data on application startup:
 * - Two users: alice and bob
 * - Each user gets SGD and USD accounts with fixed starting balances
 * - Hardcoded exchange rates for SGD, USD, and EUR pairs
 *
 * Only seeds data if users don't already exist (idempotent).
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppUserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public DataSeeder(AppUserRepository userRepository,
                      AccountRepository accountRepository,
                      ExchangeRateRepository exchangeRateRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        seedExchangeRates();
    }

    private void seedUsers() {
        if (userRepository.existsByUsername("alice")) {
            log.info("Seed data already exists, skipping user/account seeding");
            return;
        }

        log.info("Seeding users and accounts...");

        // Create users
        AppUser alice = userRepository.save(new AppUser("alice", "password"));
        AppUser bob = userRepository.save(new AppUser("bob", "password"));

        // Alice's accounts: SGD 10,000 and USD 5,000
        accountRepository.save(new Account(alice, "SGD", new BigDecimal("10000.0000")));
        accountRepository.save(new Account(alice, "USD", new BigDecimal("5000.0000")));

        // Bob's accounts: SGD 8,000 and USD 3,000
        accountRepository.save(new Account(bob, "SGD", new BigDecimal("8000.0000")));
        accountRepository.save(new Account(bob, "USD", new BigDecimal("3000.0000")));

        log.info("Seeded users: alice (SGD 10000, USD 5000), bob (SGD 8000, USD 3000)");
    }

    private void seedExchangeRates() {
        if (exchangeRateRepository.count() > 0) {
            log.info("Exchange rates already exist, skipping seeding");
            return;
        }

        log.info("Seeding exchange rates...");

        exchangeRateRepository.save(new ExchangeRate("SGD", "USD", new BigDecimal("0.74000000")));
        exchangeRateRepository.save(new ExchangeRate("USD", "SGD", new BigDecimal("1.35000000")));
        exchangeRateRepository.save(new ExchangeRate("EUR", "SGD", new BigDecimal("1.45000000")));
        exchangeRateRepository.save(new ExchangeRate("SGD", "EUR", new BigDecimal("0.69000000")));
        exchangeRateRepository.save(new ExchangeRate("EUR", "USD", new BigDecimal("1.08000000")));
        exchangeRateRepository.save(new ExchangeRate("USD", "EUR", new BigDecimal("0.93000000")));

        log.info("Seeded 6 exchange rate pairs (SGD/USD, EUR/SGD, EUR/USD)");
    }
}
