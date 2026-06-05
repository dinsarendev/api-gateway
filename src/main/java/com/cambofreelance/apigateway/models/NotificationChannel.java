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
@Table("notification_channel")
public class NotificationChannel {

    public static final String TYPE_EMAIL    = "EMAIL";
    public static final String TYPE_TELEGRAM = "TELEGRAM";
    public static final String TYPE_SLACK    = "SLACK";

    @Id
    private Long id;

    private String name;
    private String type;

    /** JSON config — EMAIL: {"recipients":["a@b.com"]}
     *               TELEGRAM: {"bot_token":"...","chat_id":"..."}
     *               SLACK:    {"webhook_url":"..."} */
    private String config;

    private boolean enabled;

    @Column("min_severity")
    private String minSeverity;

    @Column("on_open")
    private boolean onOpen;

    @Column("on_acknowledge")
    private boolean onAcknowledge;

    @Column("on_resolve")
    private boolean onResolve;

    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("created_by")
    private String createdBy;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("updated_by")
    private String updatedBy;
}
