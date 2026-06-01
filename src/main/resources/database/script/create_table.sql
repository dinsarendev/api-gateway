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