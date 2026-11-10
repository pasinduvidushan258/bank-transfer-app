package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;
    private final CustomerRepository customerRepository;
    private final BankTransactionRepository bankTransactionRepository;

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String keyword, Model model) {

        if (keyword != null && !keyword.isEmpty()) {
            model.addAttribute("customers",
                    customerRepository.searchCustomerAccountsByName(keyword, Customer.BANK_ACCOUNT_NUMBER));
        } else {
            model.addAttribute("customers",
                    customerRepository.findAllCustomerAccounts(Customer.BANK_ACCOUNT_NUMBER));
        }
        model.addAttribute("keyword", keyword);

        model.addAttribute("allCustomers", customerRepository.findAllOrdered());

        model.addAttribute("totalCustomers",
                customerRepository.countCustomerAccounts(Customer.BANK_ACCOUNT_NUMBER));
        model.addAttribute("totalBalance", customerRepository.getTotalBalance());
        model.addAttribute("successCount", bankTransactionRepository.countByStatus(TransactionStatus.SUCCESS));
        model.addAttribute("failedCount", bankTransactionRepository.countByStatus(TransactionStatus.FAILED));

        model.addAttribute("transactions", bankTransactionRepository.findAllLatest());

        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }

        return "bank";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute TransferRequest transferRequest,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return dashboard(null, model);
        }

        return processTransfer(transferRequest, redirectAttributes);
    }

    @PostMapping(value = "/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String transferFromPostman(@Valid @RequestBody TransferRequest transferRequest,
                                      BindingResult result,
                                      Model model) {
        if (result.hasErrors()) {
            model.addAttribute("transferRequest", transferRequest);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "transferRequest", result);
            model.addAttribute("error", "Invalid form data. Please check the amounts.");
            return dashboard(null, model);
        }

        model.addAttribute("transferRequest", transferRequest);
        try {
            bankService.transfer(
                    transferRequest.getFromAccount(),
                    transferRequest.getToAccount(),
                    transferRequest.getAmount(),
                    transferRequest.isSimulateFailure());
            model.addAttribute("success", "Transfer completed successfully!");
        } catch (BankException exception) {
            auditService.recordFailure(
                    transferRequest.getFromAccount(),
                    transferRequest.getToAccount(),
                    transferRequest.getAmount(),
                    exception.getMessage());
            model.addAttribute("error", "Transfer failed: " + exception.getMessage());
        } catch (DataAccessException exception) {
            auditService.recordFailure(
                    transferRequest.getFromAccount(),
                    transferRequest.getToAccount(),
                    transferRequest.getAmount(),
                    "Database error while processing transfer");
            model.addAttribute("error",
                    "Transfer could not be completed because of a database error.");
        }
        return dashboard(null, model);
    }

    private String processTransfer(TransferRequest transferRequest, RedirectAttributes redirectAttributes) {
        try {
            bankService.transfer(
                    transferRequest.getFromAccount(),
                    transferRequest.getToAccount(),
                    transferRequest.getAmount(),
                    transferRequest.isSimulateFailure()
            );
            redirectAttributes.addFlashAttribute("success", "Transfer completed successfully!");

        } catch (BankException e) {
            auditService.recordFailure(
                    transferRequest.getFromAccount(),
                    transferRequest.getToAccount(),
                    transferRequest.getAmount(),
                    e.getMessage()
            );
            redirectAttributes.addFlashAttribute("error", "Transfer failed: " + e.getMessage());
        } catch (DataAccessException e) {
            auditService.recordFailure(
                    transferRequest.getFromAccount(),
                    transferRequest.getToAccount(),
                    transferRequest.getAmount(),
                    "Database error while processing transfer"
            );
            redirectAttributes.addFlashAttribute("error", "Transfer could not be completed because of a database error.");
        }

        return "redirect:/";
    }

    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute Customer customer,
                              BindingResult result,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Invalid customer data.");
            return "redirect:/";
        }
        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("success", "Customer added successfully!");
        } catch (BankException e) {
            redirectAttributes.addFlashAttribute("error", "Error adding customer: " + e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("error", "Account number or email address already exists.");
        } catch (DataAccessException e) {
            redirectAttributes.addFlashAttribute("error", "Customer could not be saved because of a database error.");
        }
        return "redirect:/";
    }

    @GetMapping("/deposit")
    public String depositPage(Model model) {
        model.addAttribute("customers", customerRepository.findAllCustomerAccounts(Customer.BANK_ACCOUNT_NUMBER));
        return "deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam String accountNumber, @RequestParam BigDecimal amount,
                          RedirectAttributes redirectAttributes) {
        if (!isValidAmount(amount)) {
            redirectAttributes.addFlashAttribute("error", "Amount must be positive and have at most two decimal places.");
            return "redirect:/deposit";
        }
        try {
            bankService.deposit(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "Deposit completed successfully!");
        } catch (BankException e) {
            auditService.recordFailure("EXTERNAL", accountNumber, amount, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Deposit failed: " + e.getMessage());
        } catch (DataAccessException e) {
            auditService.recordFailure("EXTERNAL", accountNumber, amount, "Database error while processing deposit");
            redirectAttributes.addFlashAttribute("error", "Deposit could not be completed because of a database error.");
        }
        return "redirect:/deposit";
    }

    @GetMapping("/withdraw")
    public String withdrawPage(Model model) {
        model.addAttribute("customers", customerRepository.findAllCustomerAccounts(Customer.BANK_ACCOUNT_NUMBER));
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam String accountNumber, @RequestParam BigDecimal amount,
                           RedirectAttributes redirectAttributes) {
        if (!isValidAmount(amount)) {
            redirectAttributes.addFlashAttribute("error", "Amount must be positive and have at most two decimal places.");
            return "redirect:/withdraw";
        }
        try {
            bankService.withdraw(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "Withdrawal completed successfully!");
        } catch (BankException e) {
            auditService.recordFailure(accountNumber, "CASH WITHDRAWAL", amount, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Withdrawal failed: " + e.getMessage());
        } catch (DataAccessException e) {
            auditService.recordFailure(accountNumber, "CASH WITHDRAWAL", amount,
                    "Database error while processing withdrawal");
            redirectAttributes.addFlashAttribute("error", "Withdrawal could not be completed because of a database error.");
        }
        return "redirect:/withdraw";
    }

    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(defaultValue = "10000.00") BigDecimal min, Model model) {
        if (min.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum amount cannot be negative");
        }
        model.addAttribute("min", min);
        model.addAttribute("transfers", bankTransactionRepository.findSuccessfulTransfersAbove(
                min, Customer.BANK_ACCOUNT_NUMBER));
        return "large-transfers";
    }

    @GetMapping("/accounts/{no}/history")
    public String accountHistory(@PathVariable("no") String accountNumber, Model model) {
        Customer customer = customerRepository.getByAccountNumber(accountNumber);
        if (customer == null || Customer.BANK_ACCOUNT_NUMBER.equals(accountNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        var transactions = bankTransactionRepository.findHistory(accountNumber);
        BigDecimal totalSent = bankTransactionRepository.sumSuccessfulTransfersFromAccount(
                accountNumber, TransactionStatus.SUCCESS);
        BigDecimal totalFees = transactions.stream()
                .filter(transaction -> transaction.getStatus() == TransactionStatus.SUCCESS)
                .filter(transaction -> accountNumber.equals(transaction.getFromAccount()))
                .filter(transaction -> !"CASH WITHDRAWAL".equals(transaction.getToAccount()))
                .map(transaction -> BankService.calculateTransferFee(transaction.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalSent = totalSent.add(totalFees);

        model.addAttribute("customer", customer);
        model.addAttribute("transactions", transactions);
        model.addAttribute("totalSent", totalSent);
        return "history";
    }

    private boolean isValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0
                || amount.compareTo(new BigDecimal("999999999999.99")) > 0) {
            return false;
        }
        try {
            amount.setScale(2, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }
}