CREATE TABLE public.api_group_route (
    id bigserial NOT NULL,
    code varchar(255) NULL,
    uri varchar(255) NULL,       -- use 'lb://<CODE>' to enable service discovery load balancing
    updated_at timestamp(6) NULL,
    updated_by varchar(255) NULL,
    created_at timestamp(6) NULL,
    created_by varchar(255) NULL,
    status varchar(255) NULL,
    CONSTRAINT api_group_route_pkey PRIMARY KEY (id)
);

CREATE TABLE public.service_instance (
    id               bigserial    NOT NULL,
    service_id       varchar(100) NOT NULL,                  -- matches api_group_route.code (e.g. 'AUTH')
    host             varchar(255) NOT NULL,                  -- IP address or hostname
    port             int4         NOT NULL,                  -- service port
    secure           bool         NOT NULL DEFAULT false,    -- true = HTTPS
    weight           int4         NOT NULL DEFAULT 1,        -- relative weight for load balancing
    status           varchar(10)  NOT NULL DEFAULT 'ACT',    -- ACT | INACT  (record lifecycle)
    health_status    varchar(20)  NOT NULL DEFAULT 'UP',     -- UP | DOWN | OUT_OF_SERVICE  (runtime health)
    last_health_check timestamp   NULL,                      -- last probe timestamp
    created_at       timestamp(6) NULL,
    created_by       varchar(255) NULL,
    updated_at       timestamp(6) NULL,
    updated_by       varchar(255) NULL,
    CONSTRAINT service_instance_pkey PRIMARY KEY (id)
);

-- health_path: custom probe endpoint per instance; NULL = skip probe (always UP)
ALTER TABLE public.service_instance
    ADD COLUMN IF NOT EXISTS health_path varchar(255) NULL DEFAULT '/actuator/health';

CREATE INDEX idx_service_instance_service_id   ON public.service_instance (service_id);
CREATE INDEX idx_service_instance_status       ON public.service_instance (status);
CREATE INDEX idx_service_instance_health       ON public.service_instance (health_status);

CREATE TABLE public.api_route (
      id bigserial NOT NULL,
      group_code varchar(255) NULL,
      application_id varchar(255) NULL,
      description varchar(255) NULL,
      is_public varchar(255) NULL,
      "method" varchar(255) NULL,
      "path" varchar(255) NULL,
      rate_limit int4 NULL,
      rate_limit_duration int4 NULL,
      created_at timestamp(6) NULL,
      created_by varchar(255) NULL,
      status varchar(255) NULL,
      updated_at timestamp(6) NULL,
      updated_by varchar(255) NULL,
      remark varchar NULL,
      is_encrypt varchar(1) DEFAULT 'N'::character varying NULL,
      priority int4 DEFAULT 1 NULL,
      start_time timestamp NULL,
      end_time timestamp NULL,
      enable_circuit_breaker varchar(1) DEFAULT 'Y'::character varying NULL,
      CONSTRAINT api_route_pkey PRIMARY KEY (id)
);

-- Security policy columns on api_route
ALTER TABLE public.api_route
    ADD COLUMN IF NOT EXISTS auth_type varchar(20) DEFAULT 'JWT',
    ADD COLUMN IF NOT EXISTS required_roles text NULL,
    ADD COLUMN IF NOT EXISTS required_permissions text NULL,
    ADD COLUMN IF NOT EXISTS api_type varchar(20) DEFAULT 'REST' NULL;

COMMENT ON COLUMN public.api_route.api_type IS 'REST | SOAP | GRAPHQL | STREAMING | AI';

-- ── Route versioning & deprecation ────────────────────────────────────────────
ALTER TABLE public.api_route
    ADD COLUMN IF NOT EXISTS version    varchar(20)  NULL,
    ADD COLUMN IF NOT EXISTS deprecated varchar(1)   NOT NULL DEFAULT 'N',
    ADD COLUMN IF NOT EXISTS sunset_date timestamp   NULL;

COMMENT ON COLUMN public.api_route.version     IS 'API version tag e.g. v1, v2, v3';
COMMENT ON COLUMN public.api_route.deprecated  IS 'Y = inject Deprecation + Sunset headers';
COMMENT ON COLUMN public.api_route.sunset_date IS 'Date after which the route will be retired (used in Sunset header)';

-- ── Blue-Green deployment support on api_group_route ─────────────────────────
ALTER TABLE public.api_group_route
    ADD COLUMN IF NOT EXISTS blue_uri    varchar(255) NULL,
    ADD COLUMN IF NOT EXISTS green_uri   varchar(255) NULL,
    ADD COLUMN IF NOT EXISTS active_slot varchar(5)   NOT NULL DEFAULT 'BLUE';

-- Seed blue_uri from the existing uri column for all groups that have one
UPDATE public.api_group_route SET blue_uri = uri WHERE blue_uri IS NULL AND uri IS NOT NULL;

COMMENT ON COLUMN public.api_group_route.blue_uri    IS 'Blue slot backend URI (stable)';
COMMENT ON COLUMN public.api_group_route.green_uri   IS 'Green slot backend URI (new version)';
COMMENT ON COLUMN public.api_group_route.active_slot IS 'BLUE | GREEN — which slot receives live traffic';

-- ── Security tables ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.ip_access_control (
    id          bigserial    NOT NULL,
    type        varchar(10)  NOT NULL,               -- WHITELIST | BLACKLIST
    ip_cidr     varchar(50)  NOT NULL,               -- 10.0.0.1  or  192.168.0.0/24
    scope       varchar(10)  NOT NULL DEFAULT 'GLOBAL', -- GLOBAL | GROUP | ROUTE
    scope_id    varchar(255) NULL,                   -- group_code or route id
    description varchar(255) NULL,
    status      varchar(10)  NOT NULL DEFAULT 'ACT',
    created_at  timestamp(6) NULL,
    created_by  varchar(255) NULL,
    updated_at  timestamp(6) NULL,
    updated_by  varchar(255) NULL,
    CONSTRAINT ip_access_control_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ip_acl_type_status ON public.ip_access_control (type, status);

CREATE TABLE IF NOT EXISTS public.api_key (
    id           bigserial    NOT NULL,
    name         varchar(255) NOT NULL,
    key_prefix   varchar(12)  NOT NULL,   -- first chars shown in UI (non-secret)
    key_hash     varchar(255) NOT NULL,   -- bcrypt hash of full raw key
    client_id    varchar(255) NULL,
    roles        text         NULL,       -- comma-separated roles granted
    permissions  text         NULL,       -- comma-separated permissions granted
    status       varchar(10)  NOT NULL DEFAULT 'ACT',
    expires_at   timestamp    NULL,
    last_used_at timestamp    NULL,
    created_at   timestamp(6) NULL,
    created_by   varchar(255) NULL,
    updated_at   timestamp(6) NULL,
    updated_by   varchar(255) NULL,
    CONSTRAINT api_key_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_api_key_status ON public.api_key (status);

-- ── Admin authentication tables ───────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.admin_user (
    id            bigserial    NOT NULL,
    username      varchar(100) NOT NULL,
    password_hash varchar(255) NOT NULL,
    email         varchar(255) NULL,
    full_name     varchar(255) NULL,
    status        varchar(10)  NOT NULL DEFAULT 'ACT',
    last_login_at timestamp    NULL,
    created_at    timestamp(6) NULL,
    created_by    varchar(255) NULL,
    updated_at    timestamp(6) NULL,
    updated_by    varchar(255) NULL,
    CONSTRAINT admin_user_pkey    PRIMARY KEY (id),
    CONSTRAINT admin_user_uq_name UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS public.admin_role (
    id          bigserial    NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(255) NULL,
    status      varchar(10)  NOT NULL DEFAULT 'ACT',
    created_at  timestamp(6) NULL,
    created_by  varchar(255) NULL,
    updated_at  timestamp(6) NULL,
    updated_by  varchar(255) NULL,
    CONSTRAINT admin_role_pkey    PRIMARY KEY (id),
    CONSTRAINT admin_role_uq_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS public.admin_permission (
    id          bigserial    NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(255) NULL,
    status      varchar(10)  NOT NULL DEFAULT 'ACT',
    created_at  timestamp(6) NULL,
    created_by  varchar(255) NULL,
    updated_at  timestamp(6) NULL,
    updated_by  varchar(255) NULL,
    CONSTRAINT admin_permission_pkey    PRIMARY KEY (id),
    CONSTRAINT admin_permission_uq_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS public.user_role (
    id      bigserial NOT NULL,
    user_id bigint    NOT NULL,
    role_id bigint    NOT NULL,
    CONSTRAINT user_role_pkey PRIMARY KEY (id),
    CONSTRAINT user_role_uniq UNIQUE (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS idx_user_role_user ON public.user_role (user_id);

CREATE TABLE IF NOT EXISTS public.role_permission (
    id            bigserial NOT NULL,
    role_id       bigint    NOT NULL,
    permission_id bigint    NOT NULL,
    CONSTRAINT role_permission_pkey PRIMARY KEY (id),
    CONSTRAINT role_permission_uniq UNIQUE (role_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_role_permission_role ON public.role_permission (role_id);

CREATE TABLE IF NOT EXISTS public.admin_refresh_token (
    id          bigserial    NOT NULL,
    user_id     bigint       NOT NULL,
    token_hash  varchar(255) NOT NULL,
    expires_at  timestamp    NOT NULL,
    revoked     boolean      NOT NULL DEFAULT false,
    created_at  timestamp(6) NULL,
    CONSTRAINT admin_refresh_token_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON public.admin_refresh_token (token_hash);

CREATE TABLE IF NOT EXISTS public.oauth2_provider (
    id                 bigserial    NOT NULL,
    name               varchar(100) NOT NULL UNIQUE,
    introspection_uri  varchar(500) NOT NULL,
    client_id          varchar(255) NOT NULL,
    client_secret      varchar(500) NOT NULL,
    status             varchar(10)  NOT NULL DEFAULT 'ACT',
    created_at         timestamp(6) NULL,
    created_by         varchar(255) NULL,
    updated_at         timestamp(6) NULL,
    updated_by         varchar(255) NULL,
    CONSTRAINT oauth2_provider_pkey PRIMARY KEY (id)
);