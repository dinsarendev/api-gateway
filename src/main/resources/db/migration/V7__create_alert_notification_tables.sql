-- ── Incident: add acknowledge tracking fields ──────────────────────────────────
ALTER TABLE public.incident
    ADD COLUMN IF NOT EXISTS acknowledged_at  timestamp(6) NULL,
    ADD COLUMN IF NOT EXISTS acknowledged_by  varchar(255) NULL;

-- ── Alert history: every state transition on an incident ──────────────────────
-- action: OPENED | ACKNOWLEDGED | RESOLVED | CLOSED | UPDATED | NOTE
CREATE TABLE IF NOT EXISTS public.alert_history (
    id           bigserial     NOT NULL,
    incident_id  bigint        NOT NULL,
    action       varchar(30)   NOT NULL,
    old_status   varchar(20)   NULL,
    new_status   varchar(20)   NULL,
    note         varchar(1000) NULL,
    performed_by varchar(255)  NULL,
    performed_at timestamp(6)  NOT NULL DEFAULT NOW(),
    CONSTRAINT alert_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk_alert_history_incident FOREIGN KEY (incident_id)
        REFERENCES public.incident (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_alert_history_incident_id  ON public.alert_history (incident_id);
CREATE INDEX IF NOT EXISTS idx_alert_history_performed_at ON public.alert_history (performed_at DESC);

-- ── Notification channels ──────────────────────────────────────────────────────
-- type:         EMAIL | TELEGRAM | SLACK
-- min_severity: NULL = all; CRITICAL | HIGH | MEDIUM | LOW
-- config:       JSON — EMAIL: {"recipients":["a@b.com"]}
--                       TELEGRAM: {"bot_token":"...","chat_id":"..."}
--                       SLACK:    {"webhook_url":"https://hooks.slack.com/..."}
CREATE TABLE IF NOT EXISTS public.notification_channel (
    id             bigserial    NOT NULL,
    name           varchar(100) NOT NULL,
    type           varchar(20)  NOT NULL,
    config         text         NOT NULL,
    enabled        boolean      NOT NULL DEFAULT true,
    min_severity   varchar(20)  NULL,
    on_open        boolean      NOT NULL DEFAULT true,
    on_acknowledge boolean      NOT NULL DEFAULT false,
    on_resolve     boolean      NOT NULL DEFAULT true,
    status         varchar(10)  NOT NULL DEFAULT 'ACT',
    created_at     timestamp(6) NOT NULL DEFAULT NOW(),
    created_by     varchar(255) NULL,
    updated_at     timestamp(6) NULL,
    updated_by     varchar(255) NULL,
    CONSTRAINT notification_channel_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_notification_channel_type    ON public.notification_channel (type);
CREATE INDEX IF NOT EXISTS idx_notification_channel_enabled ON public.notification_channel (enabled, status);
