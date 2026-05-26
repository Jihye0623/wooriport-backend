package com.wooriport.core_api.base.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(Long required, Long actual) {
        super(String.format("잔액이 부족합니다. 필요: %,d원, 현재: %,d원", required, actual));
    }
}