package com.wooriport.core_api.base.exception;

public class TransferPlanNotFoundException extends RuntimeException {
    public TransferPlanNotFoundException() {
        super("이체 계획을 찾을 수 없습니다.");
    }
}
