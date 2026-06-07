package com.cambofreelance.apigateway.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("audit_log")
public class AuditLog {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    private String actor;

    private String module;

    private String action;

    @Column("entity_id")
    private String entityId;

    @Column("old_value")
    private String oldValue;

    @Column("new_value")
    private String newValue;

    private String method;

    private String path;

    @Column("status_code")
    private Integer statusCode;

    private String result;

    @Column("ip_address")
    private String ipAddress;

    @Column("created_at")
    private LocalDateTime createdAt;
}