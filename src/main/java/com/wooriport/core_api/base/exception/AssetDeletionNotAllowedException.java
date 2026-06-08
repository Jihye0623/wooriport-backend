package com.wooriport.core_api.base.exception;

public class AssetDeletionNotAllowedException extends RuntimeException {
    public AssetDeletionNotAllowedException(String message) {
        super(message);
    }
}
