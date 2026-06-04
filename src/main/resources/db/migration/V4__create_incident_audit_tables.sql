-- ── Incidents ──────────────────────────────────────────────────────────────────
-- severity:  CRITICAL | HIGH | MEDIUM | LOW
-- status:    OPEN | INVESTIGATING | RESOLVED | CLOSED
-- type:      AVAILABILITY | PERFORMANCE | ERROR | INFRASTRUCTURE
-- source:    MANUAL | AUTO
CREATE TABLE IF NOT EXISTS public.incident (
    id                bigserial     NOT NULL,
    title             varchar(500)  NOT NULL,
    description       varchar(2000) NULL,
    severity          varchar(20)   NOT NULL DEFAULT 'HIGH',
    status            varchar(20)   NOT NULL DEFAULT 'OPEN',
    type              varchar(30)   NOT NULL,
    trigger_key       varchar(255)  NULL,
    trigger_value     varchar(100)  NULL,
    trigger_threshold varchar(100)  NULL,
    affected_service  varchar(255)  NULL,
    affected_route    varchar(500)  NULL,
    source            varchar(10)   NOT NULL DEFAULT 'MANUAL',
    opened_at         timestamp(6)  NOT NULL DEFAULT NOW(),
    resolved_at       timestamp(6)  NULL,
    updated_at        timestamp(6)  NULL,
    updated_by        varchar(255)  NULL,
    created_by        varchar(255)  NULL,
    CONSTRAINT incident_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.incident.trigger_key       IS 'deduplication key for auto-created incidents';
COMMENT ON COLUMN public.incident.trigger_value     IS 'actual measured value (e.g. "96.2%")';
COMMENT ON COLUMN public.incident.trigger_threshold IS 'threshold that was breached (e.g. "10%")';
COMMENT ON COLUMN public.incident.affected_service  IS 'group_code';
COMMENT ON COLUMN public.incident.affected_route    IS 'path';

CREATE INDEX IF NOT EXISTS idx_incident_status      ON public.incident (status);
CREATE INDEX IF NOT EXISTS idx_incident_severity    ON public.incident (severity);
CREATE INDEX IF NOT EXISTS idx_incident_opened_at   ON public.incident (opened_at DESC);
CREATE INDEX IF NOT EXISTS idx_incident_trigger_key ON public.incident (trigger_key) WHERE trigger_key IS NOT NULL;

-- ── Audit log ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.audit_log (
    id          bigserial    NOT NULL,
    user_id     bigint       NULL,
    actor       varchar(100) NULL,
    module      varchar(50)  NOT NULL,
    action      varchar(30)  NOT NULL,
    entity_id   varchar(100) NULL,
    old_value   text         NULL,
    new_value   text         NULL,
    method      varchar(10)  NOT NULL,
    path        varchar(500) NOT NULL,
    status_code int          NULL,
    result      varchar(10)  NOT NULL DEFAULT 'SUCCESS',
    ip_address  varchar(100) NULL,
    created_at  timestamp(6) NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_log_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor      ON public.audit_log (actor);
CREATE INDEX IF NOT EXISTS idx_audit_log_module     ON public.audit_log (module);
CREATE INDEX IF NOT EXISTS idx_audit_log_action     ON public.audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON public.audit_log (created_at DESC);
