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
@Table("alert_history")
public class AlertHistory {

    public static final String ACTION_OPENED       = "OPENED";
    public static final String ACTION_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String ACTION_RESOLVED     = "RESOLVED";
    public static final String ACTION_CLOSED       = "CLOSED";
    public static final String ACTION_UPDATED      = "UPDATED";
    public static final String ACTION_NOTE         = "NOTE";

    @Id
    private Long id;

    @Column("incident_id")
    private Long incidentId;

    private String action;

    @Column("old_status")
    private String oldStatus;

    @Column("new_status")
    private String newStatus;

    private String note;

    @Column("performed_by")
    private String performedBy;

    @Column("performed_at")
    private LocalDateTime performedAt;
}
