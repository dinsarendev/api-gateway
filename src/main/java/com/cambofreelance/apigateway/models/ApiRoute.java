package com.cambofreelance.apigateway.models;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = false)
@Table("api_route")
public class ApiRoute extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 7013891582242164719L;

    @Id
    private Long id;

    @Column("uri") // joined from api_group_route using custom query
    private String uri;

    @Column("path")
    private String path;

    @Column("method")
    private String method;

    @Column("group_code")
    private String groupCode;

    @Column("description")
    private String description;

    @Column("application_id")
    private String applicationId;

    @Column("rate_limit")
    private Integer rateLimit;

    @Column("rate_limit_duration")
    private Integer rateLimitDuration;

    @Column("is_public")
    private String isPublic;

    @Column("is_encrypt")
    private String isEncrypt;

    @Column("status")
    private String status;

    @Column("priority")
    private Integer priority;

    @Column("start_time")
    private LocalDateTime startTime;

    @Column("end_time")
    private LocalDateTime endTime;

    @Column("enable_circuit_breaker")
    private String enableCircuitBreaker;

    @Column("auth_type")
    private String authType;

    @Column("required_roles")
    private String requiredRoles;

    @Column("required_permissions")
    private String requiredPermissions;
}
