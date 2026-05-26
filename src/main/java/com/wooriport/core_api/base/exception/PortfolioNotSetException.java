package com.wooriport.core_api.base.exception;

public class PortfolioNotSetException extends RuntimeException {
    public PortfolioNotSetException() {
        super("포트폴리오가 설정되지 않았습니다.");
    }
}
