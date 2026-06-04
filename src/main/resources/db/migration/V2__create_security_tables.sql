-- ── IP access control ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.ip_access_control (
    id          bigserial    NOT NULL,
    type        varchar(10)  NOT NULL,
    ip_cidr     varchar(50)  NOT NULL,
    scope       varchar(10)  NOT NULL DEFAULT 'GLOBAL',
    scope_id    varchar(255) NULL,
    description varchar(255) NULL,
    status      varchar(10)  NOT NULL DEFAULT 'ACT',
    created_at  timestamp(6) NULL,
    created_by  varchar(255) NULL,
    updated_at  timestamp(6) NULL,
    updated_by  varchar(255) NULL,
    CONSTRAINT ip_access_control_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.ip_access_control.type     IS 'WHITELIST | BLACKLIST';
COMMENT ON COLUMN public.ip_access_control.ip_cidr  IS '10.0.0.1 or 192.168.0.0/24';
COMMENT ON COLUMN public.ip_access_control.scope    IS 'GLOBAL | GROUP | ROUTE';
COMMENT ON COLUMN public.ip_access_control.scope_id IS 'group_code or route id';

CREATE INDEX IF NOT EXISTS idx_ip_acl_type_status ON public.ip_access_control (type, status);

-- ── API keys ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.api_key (
    id           bigserial    NOT NULL,
    name         varchar(255) NOT NULL,
    key_prefix   varchar(12)  NOT NULL,
    key_hash     varchar(255) NOT NULL,
    client_id    varchar(255) NULL,
    roles        text         NULL,
    permissions  text         NULL,
    status       varchar(10)  NOT NULL DEFAULT 'ACT',
    expires_at   timestamp    NULL,
    last_used_at timestamp    NULL,
    created_at   timestamp(6) NULL,
    created_by   varchar(255) NULL,
    updated_at   timestamp(6) NULL,
    updated_by   varchar(255) NULL,
    CONSTRAINT api_key_pkey PRIMARY KEY (id)
);

COMMENT ON COLUMN public.api_key.key_prefix  IS 'first chars shown in UI (non-secret)';
COMMENT ON COLUMN public.api_key.key_hash    IS 'bcrypt hash of full raw key';
COMMENT ON COLUMN public.api_key.roles       IS 'comma-separated roles granted';
COMMENT ON COLUMN public.api_key.permissions IS 'comma-separated permissions granted';

CREATE INDEX IF NOT EXISTS idx_api_key_status ON public.api_key (status);

-- ── OAuth2 providers ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.oauth2_provider (
    id                bigserial    NOT NULL,
    name              varchar(100) NOT NULL UNIQUE,
    introspection_uri varchar(500) NOT NULL,
    client_id         varchar(255) NOT NULL,
    client_secret     varchar(500) NOT NULL,
    status            varchar(10)  NOT NULL DEFAULT 'ACT',
    created_at        timestamp(6) NULL,
    created_by        varchar(255) NULL,
    updated_at        timestamp(6) NULL,
    updated_by        varchar(255) NULL,
    CONSTRAINT oauth2_provider_pkey PRIMARY KEY (id)
);
