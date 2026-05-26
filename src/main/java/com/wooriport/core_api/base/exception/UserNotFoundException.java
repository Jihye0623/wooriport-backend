package com.wooriport.core_api.base.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다.");
    }
}