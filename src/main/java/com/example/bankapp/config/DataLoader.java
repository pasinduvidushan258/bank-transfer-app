package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

/** Inserts three sample customers the first time the application starts (empty database). */
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    // call the CustomerRepository to save sample customers
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (customerRepository.countAccounts(Customer.BANK_ACCOUNT_NUMBER) == 0) {
            customerRepository.save(new Customer(null, Customer.BANK_ACCOUNT_NUMBER, "Bank Fee Account",
                    "bank-fees@bankapp.local", BigDecimal.ZERO));
        }

        if (customerRepository.countCustomerAccounts(Customer.BANK_ACCOUNT_NUMBER) == 0) {
            Customer c1 = new Customer(null, "100001", "Kamal", "kamal@gmail.com", new BigDecimal("45000.00"));
            Customer c2 = new Customer(null, "100002", "Nimali", "nimali@gmail.com", new BigDecimal("30000.00"));
            Customer c3 = new Customer(null, "100003", "Saman", "saman@gmail.com", new BigDecimal("10000.00"));
            customerRepository.saveAll(List.of(c1, c2, c3));
            System.out.println("Sample data loaded successfully.");
        }
    }
}