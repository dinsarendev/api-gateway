package com.cambofreelance.apigateway.dto;


import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResponseCode extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 7013891582242164719L;
    private Long id;
    private String code;
    private String httpStatus;
    private String key;
    private String type;
    private String description;
    private String message;
    private String messageKh;
    private String messageCn;
    private String status;

}
