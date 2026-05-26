package com.wooriport.core_api.base.exception;

public class PortiNotSetException extends RuntimeException {
    public PortiNotSetException() {
        super("porTI 검사를 먼저 완료해주세요.");
    }
}
