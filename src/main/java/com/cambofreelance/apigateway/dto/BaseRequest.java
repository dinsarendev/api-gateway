package com.cambofreelance.apigateway.dto;

import lombok.Data;

@Data
public class BaseRequest {
    private String payload;
    private String iv;
}
