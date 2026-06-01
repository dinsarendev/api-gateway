package com.cambofreelance.apigateway.models;

import jakarta.persistence.MappedSuperclass;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.cambofreelance.apigateway.constants.Constants;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@ToString
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@MappedSuperclass
public class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = -8892838641805537110L;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String createdBy = Constants.SYSTEM;
    private LocalDateTime updatedAt;
    private String updatedBy;
    @Builder.Default
    private String status = Constants.STATUS_ACTIVE;

}
