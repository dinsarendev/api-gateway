package com.cambofreelance.apigateway.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.cambofreelance.apigateway.constants.Constants;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = -8892838641805537110L;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String createdBy = Constants.SYSTEM;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String status = Constants.STATUS_ACTIVE;

}
