package com.wooriport.core_api.base.exception;

public class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException(String message) {
        super(message);
    }
    public AssetNotFoundException() {
        super("계좌를 찾을 수 없습니다.");
    }
}