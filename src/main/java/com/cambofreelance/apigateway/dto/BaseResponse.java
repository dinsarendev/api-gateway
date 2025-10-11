package com.cambofreelance.apigateway.dto;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BaseResponse<T> {
    private boolean success;
    private long timestamp;
    private String code;
    private String message;
    private T data;
    private String traceId;
    private String iv;

    public static <T> BaseResponse<T> OK(T data) {
        return BaseResponse.<T>builder()
                .success(true)
                .timestamp(new Date().getTime())
                .data(data)
                .message("200")
                .code("200")
                .build();
    }
}
