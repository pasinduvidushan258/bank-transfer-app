package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.exception.SameAccountException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // default for every method: read-only transaction
public class BankService {

    private static final BigDecimal TRANSFER_FEE_RATE = new BigDecimal("0.01");
    private static final BigDecimal MAXIMUM_BALANCE = new BigDecimal("999999999999.99");

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository bankTransactionRepository;

    // Save a new customer (Needs write access, so we override the class level read-only)
    @Transactional
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number already exists");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email address already exists");
        }
        return customerRepository.save(customer);
    }

    // Main transfer method. Rolls back for ANY Exception (including checked exceptions).
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        amount = validateAmount(amount);
        if (fromAccount == null || fromAccount.isBlank() || toAccount == null || toAccount.isBlank()) {
            throw new BankException("Sender and receiver accounts are required");
        }
        if (fromAccount.equals(toAccount)) {
            throw new SameAccountException();
        }

        Customer sender = customerRepository.getByAccountNumber(fromAccount);
        if (sender == null) {
            throw new AccountNotFoundException(fromAccount);
        }

        Customer receiver = customerRepository.getByAccountNumber(toAccount);
        if (receiver == null) {
            throw new AccountNotFoundException(toAccount);
        }

        BigDecimal fee = calculateTransferFee(amount);
        BigDecimal totalDebit = amount.add(fee);
        if (totalDebit.compareTo(MAXIMUM_BALANCE) > 0) {
            throw new BankException("Transfer amount exceeds the supported limit");
        }

        int updatedRows = customerRepository.debit(fromAccount, totalDebit);
        if (updatedRows == 0) {
            throw new InsufficientBalanceException(fromAccount);
        }

        if (simulateFailure) {
            throw new BankException("All changes were rolled back");
        }

        creditAccount(toAccount, amount);
        if (fee.signum() > 0) {
            creditAccount(Customer.BANK_ACCOUNT_NUMBER, fee);
        }

        return saveTransaction(amount, "Transfer completed; fee: " + fee.toPlainString(),
                fromAccount, toAccount, TransactionStatus.SUCCESS);
    }

    @Transactional
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        amount = validateAmount(amount);
        requireCustomerAccount(accountNumber);
        creditAccount(accountNumber, amount);
        return saveTransaction(amount, "Cash deposit", "EXTERNAL", accountNumber, TransactionStatus.SUCCESS);
    }

    @Transactional
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        amount = validateAmount(amount);
        requireCustomerAccount(accountNumber);
        if (customerRepository.debit(accountNumber, amount) == 0) {
            throw new InsufficientBalanceException(accountNumber);
        }
        return saveTransaction(amount, "Cash withdrawal", accountNumber, "CASH WITHDRAWAL",
                TransactionStatus.SUCCESS);
    }

    public static BigDecimal calculateTransferFee(BigDecimal amount) {
        return amount.multiply(TRANSFER_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    private Customer requireCustomerAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()
                || Customer.BANK_ACCOUNT_NUMBER.equals(accountNumber)) {
            throw new AccountNotFoundException(accountNumber);
        }
        Customer customer = customerRepository.getByAccountNumber(accountNumber);
        if (customer == null) {
            throw new AccountNotFoundException(accountNumber);
        }
        return customer;
    }

    private void creditAccount(String accountNumber, BigDecimal amount) {
        if (customerRepository.credit(accountNumber, amount) != 1) {
            throw new AccountNotFoundException(accountNumber);
        }
    }

    private BankTransaction saveTransaction(BigDecimal amount, String description, String fromAccount,
                                            String toAccount, TransactionStatus status) {
        return bankTransactionRepository.save(new BankTransaction(
                null, amount, LocalDateTime.now(), description, fromAccount, toAccount, status));
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankException("Amount must be greater than zero");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BankException("Amount must have no more than two decimal places");
        }
        if (amount.compareTo(MAXIMUM_BALANCE) > 0) {
            throw new BankException("Amount exceeds the supported limit");
        }
        return amount;
    }

}
