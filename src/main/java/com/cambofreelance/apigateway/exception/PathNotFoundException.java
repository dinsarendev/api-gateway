package com.cambofreelance.apigateway.exception;

public class PathNotFoundException extends RuntimeException {

    public PathNotFoundException(String message) {
        super(message);
    }
}

