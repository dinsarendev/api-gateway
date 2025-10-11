package com.cambofreelance.apigateway.exception;

public class UriNotFoundException extends RuntimeException {

    public UriNotFoundException(String message) {
        super(message);
    }
}
