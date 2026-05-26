package com.wooriport.core_api.base.exception;

public class SalaryNotFoundException extends RuntimeException {
    public SalaryNotFoundException() {
        super("급여 트랜잭션이 없습니다.");
    }
}