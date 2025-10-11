CREATE TABLE public.api_group_route (
    id bigserial NOT NULL,
    code varchar(255) NULL,
    uri varchar(255) NULL,
    updated_at timestamp(6) NULL,
    updated_by varchar(255) NULL,
    created_at timestamp(6) NULL,
    created_by varchar(255) NULL,
    status varchar(255) NULL,
    CONSTRAINT api_group_route_pkey PRIMARY KEY (id)
);

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