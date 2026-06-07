-- ── Admin users ────────────────────────────────────────────────────────────────
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

-- ── Roles ──────────────────────────────────────────────────────────────────────
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

-- ── Permissions ────────────────────────────────────────────────────────────────
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

-- ── User ↔ Role mapping ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.user_role (
    id      bigserial NOT NULL,
    user_id bigint    NOT NULL,
    role_id bigint    NOT NULL,
    CONSTRAINT user_role_pkey PRIMARY KEY (id),
    CONSTRAINT user_role_uniq UNIQUE (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_role_user ON public.user_role (user_id);

-- ── Role ↔ Permission mapping ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.role_permission (
    id            bigserial NOT NULL,
    role_id       bigint    NOT NULL,
    permission_id bigint    NOT NULL,
    CONSTRAINT role_permission_pkey PRIMARY KEY (id),
    CONSTRAINT role_permission_uniq UNIQUE (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_role_permission_role ON public.role_permission (role_id);

-- ── Refresh tokens ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.admin_refresh_token (
    id         bigserial    NOT NULL,
    user_id    bigint       NOT NULL,
    token_hash varchar(255) NOT NULL,
    expires_at timestamp    NOT NULL,
    revoked    boolean      NOT NULL DEFAULT false,
    created_at timestamp(6) NULL,
    CONSTRAINT admin_refresh_token_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_hash ON public.admin_refresh_token (token_hash);
