package com.cambofreelance.apigateway.models;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder(toBuilder = true)
@Table("ip_access_control")
public class IpAccessControl extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private String type;        // WHITELIST | BLACKLIST

    @Column("ip_cidr")
    private String ipCidr;

    private String scope;       // GLOBAL | GROUP | ROUTE

    @Column("scope_id")
    private String scopeId;

    private String description;
}