package com.example.bankapp.service;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a FAILED record after a transfer was rolled back.
 * REQUIRES_NEW = always start a brand-new, independent transaction,
 * so this record is committed even though the transfer itself was rolled back.
 */
@Service
@RequiredArgsConstructor
public class TransactionAuditService {

    private final BankTransactionRepository bankTransactionRepository;

    // REQUIRES_NEW starts a completely independent transaction to save the failed record
    // even if the main transfer transaction was rolled back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String fromAccount, String toAccount, BigDecimal amount, String errorMessage) {

        BankTransaction failedTransaction = new BankTransaction(
                null,
                amount,
                LocalDateTime.now(),
                errorMessage,
                fromAccount,
                toAccount,
                TransactionStatus.FAILED
        );

        bankTransactionRepository.save(failedTransaction);
    }

}
