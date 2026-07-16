package com.ledger.account;

import com.ledger.transaction.Transaction;
import com.ledger.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
                          AppUserRepository userRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountController.AccountDto> getAccountsByUsername(String username) {
        return accountRepository.findByUserUsername(username).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AccountController.AccountDto> getAccountById(UUID id) {
        return accountRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<AccountController.AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(UUID accountId) {
        return transactionRepository.findByAccountId(accountId);
    }

    private AccountController.AccountDto toDto(Account account) {
        return new AccountController.AccountDto(
                account.getId(),
                account.getUser().getUsername(),
                account.getCurrency(),
                account.getBalance().toPlainString()
        );
    }
}
