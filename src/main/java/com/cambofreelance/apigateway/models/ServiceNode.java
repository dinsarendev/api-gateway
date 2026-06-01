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
@Table("service_instance")
public class ServiceNode extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 3812034592167245893L;

    @Id
    private Long id;

    @Column("service_id")
    private String serviceId;

    @Column("host")
    private String host;

    @Column("port")
    private Integer port;

    @Column("secure")
    private boolean secure;

    @Column("weight")
    private Integer weight;

    /** Runtime health — UP | DOWN | OUT_OF_SERVICE. Distinct from BaseEntity.status (ACT/INACT). */
    @Column("health_status")
    private String healthStatus;

    @Column("last_health_check")
    private LocalDateTime lastHealthCheck;
}