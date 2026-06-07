package com.cambofreelance.apigateway.models;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder(toBuilder = true)
@Table("api_key")
public class ApiKey extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String name;

    @Column("key_prefix")
    private String keyPrefix;

    @Column("key_hash")
    private String keyHash;

    @Column("client_id")
    private String clientId;

    private String roles;        // comma-separated

    private String permissions;  // comma-separated

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("last_used_at")
    private LocalDateTime lastUsedAt;
}