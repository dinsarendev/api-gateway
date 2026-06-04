-- ── Route group table ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.api_group_route (
    id          bigserial    NOT NULL,
    code        varchar(255) NULL,
    uri         varchar(255) NULL,
    blue_uri    varchar(255) NULL,
    green_uri   varchar(255) NULL,
    active_slot varchar(5)   NOT NULL DEFAULT 'BLUE',
    updated_at  timestamp(6) NULL,
    updated_by  varchar(255) NULL,
    created_at  timestamp(6) NULL,
    created_by  varchar(255) NULL,
    status      varchar(255) NULL,
    CONSTRAINT api_group_route_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.api_group_route.uri         IS 'use ''lb://<CODE>'' to enable service discovery load balancing';
COMMENT ON COLUMN public.api_group_route.blue_uri    IS 'Blue slot backend URI (stable)';
COMMENT ON COLUMN public.api_group_route.green_uri   IS 'Green slot backend URI (new version)';
COMMENT ON COLUMN public.api_group_route.active_slot IS 'BLUE | GREEN — which slot receives live traffic';

-- ── Service instance table ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.service_instance (
    id                bigserial    NOT NULL,
    service_id        varchar(100) NOT NULL,
    host              varchar(255) NOT NULL,
    port              int4         NOT NULL,
    secure            bool         NOT NULL DEFAULT false,
    weight            int4         NOT NULL DEFAULT 1,
    status            varchar(10)  NOT NULL DEFAULT 'ACT',
    health_status     varchar(20)  NOT NULL DEFAULT 'UP',
    health_path       varchar(255) NULL DEFAULT '/actuator/health',
    last_health_check timestamp    NULL,
    created_at        timestamp(6) NULL,
    created_by        varchar(255) NULL,
    updated_at        timestamp(6) NULL,
    updated_by        varchar(255) NULL,
    CONSTRAINT service_instance_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.service_instance.service_id    IS 'matches api_group_route.code (e.g. ''AUTH'')';
COMMENT ON COLUMN public.service_instance.secure        IS 'true = HTTPS';
COMMENT ON COLUMN public.service_instance.weight        IS 'relative weight for load balancing';
COMMENT ON COLUMN public.service_instance.status        IS 'ACT | INACT  (record lifecycle)';
COMMENT ON COLUMN public.service_instance.health_status IS 'UP | DOWN | OUT_OF_SERVICE  (runtime health)';
COMMENT ON COLUMN public.service_instance.health_path   IS 'custom probe endpoint per instance; NULL = skip probe (always UP)';

CREATE INDEX IF NOT EXISTS idx_service_instance_service_id ON public.service_instance (service_id);
CREATE INDEX IF NOT EXISTS idx_service_instance_status     ON public.service_instance (status);
CREATE INDEX IF NOT EXISTS idx_service_instance_health     ON public.service_instance (health_status);

-- ── API route table ────────────────────────────────────────────────────────────
-- status lifecycle:
--   DRAFT → PENDING → ACT → DEPRECATED → RETIRED
--   INACT  — administratively disabled
CREATE TABLE IF NOT EXISTS public.api_route (
    id                     bigserial    NOT NULL,
    group_code             varchar(255) NULL,
    application_id         varchar(255) NULL,
    description            varchar(255) NULL,
    is_public              varchar(255) NULL,
    method                 varchar(255) NULL,
    path                   varchar(255) NULL,
    rate_limit             int4         NULL,
    rate_limit_duration    int4         NULL,
    is_encrypt             varchar(1)   DEFAULT 'N',
    priority               int4         DEFAULT 1,
    start_time             timestamp    NULL,
    end_time               timestamp    NULL,
    enable_circuit_breaker varchar(1)   DEFAULT 'Y',
    auth_type              varchar(20)  DEFAULT 'JWT',
    required_roles         text         NULL,
    required_permissions   text         NULL,
    api_type               varchar(20)  DEFAULT 'REST',
    version                varchar(20)  NULL,
    deprecated             varchar(1)   NOT NULL DEFAULT 'N',
    sunset_date            timestamp    NULL,
    tags                   varchar(500) NULL,
    sla_tier               varchar(20)  NOT NULL DEFAULT 'STANDARD',
    documentation          text         NULL,
    rejection_reason       text         NULL,
    remark                 varchar      NULL,
    status                 varchar(255) NULL,
    created_at             timestamp(6) NULL,
    created_by             varchar(255) NULL,
    updated_at             timestamp(6) NULL,
    updated_by             varchar(255) NULL,
    CONSTRAINT api_route_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.api_route.api_type          IS 'REST | SOAP | GRAPHQL | STREAMING | AI';
COMMENT ON COLUMN public.api_route.version           IS 'API version tag e.g. v1, v2, v3';
COMMENT ON COLUMN public.api_route.deprecated        IS 'Y = inject Deprecation + Sunset headers';
COMMENT ON COLUMN public.api_route.sunset_date       IS 'Date after which the route will be auto-retired';
COMMENT ON COLUMN public.api_route.tags              IS 'Comma-separated policy labels: PII, SENSITIVE, INTERNAL, BETA, PUBLIC_API';
COMMENT ON COLUMN public.api_route.sla_tier          IS 'BASIC | STANDARD | PREMIUM | CRITICAL — expected service tier';
COMMENT ON COLUMN public.api_route.documentation     IS 'Markdown documentation for this API route';
COMMENT ON COLUMN public.api_route.rejection_reason  IS 'Reason set by approver when rejecting a PENDING route';

CREATE INDEX IF NOT EXISTS idx_api_route_status ON public.api_route (status);
CREATE INDEX IF NOT EXISTS idx_api_route_sla    ON public.api_route (sla_tier);
CREATE INDEX IF NOT EXISTS idx_api_route_sunset ON public.api_route (sunset_date)
    WHERE status = 'DEPRECATED' AND sunset_date IS NOT NULL;
