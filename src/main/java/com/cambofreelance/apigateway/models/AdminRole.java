package com.cambofreelance.apigateway.models;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder(toBuilder = true)
@Table("admin_role")
public class AdminRole extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String name;

    private String description;
}