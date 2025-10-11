package com.cambofreelance.apigateway.models;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ApiGroupRoute extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 7013891582242164719L;
    private Long id;
    private String code;
    private String uri;
    private String status;

}
