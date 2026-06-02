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
@Table("incident")
public class Incident {

    // ── Severity constants ─────────────────────────────────────────────────────
    public static final String SEV_CRITICAL = "CRITICAL";
    public static final String SEV_HIGH     = "HIGH";
    public static final String SEV_MEDIUM   = "MEDIUM";
    public static final String SEV_LOW      = "LOW";

    // ── Status constants ───────────────────────────────────────────────────────
    public static final String STATUS_OPEN          = "OPEN";
    public static final String STATUS_INVESTIGATING  = "INVESTIGATING";
    public static final String STATUS_RESOLVED       = "RESOLVED";
    public static final String STATUS_CLOSED         = "CLOSED";

    // ── Type constants ─────────────────────────────────────────────────────────
    public static final String TYPE_AVAILABILITY   = "AVAILABILITY";
    public static final String TYPE_PERFORMANCE    = "PERFORMANCE";
    public static final String TYPE_ERROR          = "ERROR";
    public static final String TYPE_INFRASTRUCTURE = "INFRASTRUCTURE";

    // ── Source constants ───────────────────────────────────────────────────────
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AUTO   = "AUTO";

    @Id
    private Long id;

    private String title;
    private String description;
    private String severity;
    private String status;
    private String type;

    @Column("trigger_key")
    private String triggerKey;

    @Column("trigger_value")
    private String triggerValue;

    @Column("trigger_threshold")
    private String triggerThreshold;

    @Column("affected_service")
    private String affectedService;

    @Column("affected_route")
    private String affectedRoute;

    private String source;

    @Column("opened_at")
    private LocalDateTime openedAt;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("updated_by")
    private String updatedBy;

    @Column("created_by")
    private String createdBy;
}
