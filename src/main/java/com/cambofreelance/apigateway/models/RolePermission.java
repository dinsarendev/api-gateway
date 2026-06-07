package com.cambofreelance.apigateway.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table("role_permission")
public class RolePermission {

    @Id
    private Long id;

    @Column("role_id")
    private Long roleId;

    @Column("permission_id")
    private Long permissionId;
}