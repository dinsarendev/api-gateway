package com.cambofreelance.apigateway.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table("admin_refresh_token")
public class AdminRefreshToken {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("token_hash")
    private String tokenHash;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    private boolean revoked;

    @Column("created_at")
    private LocalDateTime createdAt;
}