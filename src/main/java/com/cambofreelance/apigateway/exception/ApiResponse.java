package com.cambofreelance.apigateway.exception;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;
    private String traceId = String.valueOf(System.nanoTime());
    private String timestamp = String.valueOf(System.currentTimeMillis());

    public ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "200", "Successfully", data);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, errorCode, message, null);
    }
}
