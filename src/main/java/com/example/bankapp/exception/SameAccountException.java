package com.example.bankapp.exception;

public class SameAccountException extends BankException {
    public SameAccountException() {
        super("Sender and receiver must be different accounts");
    }
}