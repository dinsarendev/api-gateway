package com.cambofreelance.apigateway.exception;

import com.cambofreelance.apigateway.dto.BaseResponse;
import lombok.Getter;

@Getter
public class ResponseEncryptionException extends RuntimeException {
    private final BaseResponse<Object> response;

    public ResponseEncryptionException(BaseResponse<Object> response, Throwable cause) {
        super(response.getMessage(), cause);
        this.response = response;
    }

}
