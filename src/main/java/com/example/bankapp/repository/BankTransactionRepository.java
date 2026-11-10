package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // Get all transactions, newest first
    @Query("SELECT b FROM BankTransaction b ORDER BY b.createdAt DESC")
    List<BankTransaction> findAllLatest();

    // Get transaction history for a specific account
    @Query("SELECT b FROM BankTransaction b WHERE b.fromAccount = :accountNumber OR b.toAccount = :accountNumber ORDER BY b.createdAt DESC")
    List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);

    @Query("""
            SELECT COALESCE(SUM(b.amount), 0) FROM BankTransaction b
            WHERE b.status = :status
              AND b.fromAccount = :accountNumber
              AND b.toAccount <> 'CASH WITHDRAWAL'
            """)
    BigDecimal sumSuccessfulTransfersFromAccount(
            @Param("accountNumber") String accountNumber,
            @Param("status") TransactionStatus status);

    // Get transactions by status, newest first
    @Query("SELECT b FROM BankTransaction b WHERE b.status = :status ORDER BY b.createdAt DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    @Query("""
            SELECT b FROM BankTransaction b
            WHERE b.status = com.example.bankapp.model.TransactionStatus.SUCCESS
              AND b.amount > :minimumAmount
              AND b.fromAccount <> :systemAccount
              AND b.toAccount <> :systemAccount
              AND b.fromAccount <> 'EXTERNAL'
              AND b.toAccount <> 'CASH WITHDRAWAL'
            ORDER BY b.createdAt DESC
            """)
    List<BankTransaction> findSuccessfulTransfersAbove(
            @Param("minimumAmount") BigDecimal minimumAmount,
            @Param("systemAccount") String systemAccount);

    // Count transactions by status
    @Query("SELECT COUNT(b) FROM BankTransaction b WHERE b.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

}