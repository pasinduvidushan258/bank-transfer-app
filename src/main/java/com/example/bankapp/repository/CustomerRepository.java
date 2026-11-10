package com.example.bankapp.repository;

import com.example.bankapp.model.Customer;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;


public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // JPQL + @Param: Find a customer by their account number
    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
    Customer getByAccountNumber(@Param("accountNumber") String accountNumber);

    // JPQL COUNT: Check if an account number or email already exists
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.accountNumber = :accountNumber")
    long countAccounts(@Param("accountNumber") String accountNumber);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.email = :email")
    long countEmails(@Param("email") String email);

    // JPQL ORDER BY: Find all customers ordered by their full names
    @Query("SELECT c FROM Customer c ORDER BY c.fullName ASC")
    List<Customer> findAllOrdered();

    @Query("SELECT c FROM Customer c WHERE c.accountNumber <> :systemAccount ORDER BY c.fullName ASC")
    List<Customer> findAllCustomerAccounts(@Param("systemAccount") String systemAccount);

    // JPQL LIKE: Find customers by a partial name match (case-insensitive)
    @Query("SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Customer> searchByName(@Param("keyword") String keyword);

    @Query("SELECT c FROM Customer c WHERE c.accountNumber <> :systemAccount AND LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY c.fullName ASC")
    List<Customer> searchCustomerAccountsByName(@Param("keyword") String keyword, @Param("systemAccount") String systemAccount);

    // JPQL aggregate: Calculate the total balance of all customers
    @Query("SELECT SUM(c.balance) FROM Customer c")
    BigDecimal getTotalBalance();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.accountNumber <> :systemAccount")
    long countCustomerAccounts(@Param("systemAccount") String systemAccount);

    // Native SQL: Write a raw MySQL query to count the number of customers in the database
    @Query(value = "SELECT COUNT(*) FROM customers", nativeQuery = true)
    long countCustomersNative();

    // UPDATE + @Modifying: Deduct an amount from a customer's balance if sufficient funds are available
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Customer c SET c.balance = c.balance - :amount WHERE c.accountNumber = :accountNumber AND c.balance >= :amount")
    int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    // UPDATE + @Modifying: Add an amount to a customer's balance
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Customer c SET c.balance = c.balance + :amount WHERE c.accountNumber = :accountNumber")
    int credit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

}
