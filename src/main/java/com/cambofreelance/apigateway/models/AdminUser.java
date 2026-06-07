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
@Table("admin_user")
public class AdminUser extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String username;

    @Column("password_hash")
    private String passwordHash;

    private String email;

    @Column("full_name")
    private String fullName;

    @Column("last_login_at")
    private LocalDateTime lastLoginAt;
}