INSERT INTO public.api_group_route (code, uri, blue_uri, updated_at, updated_by, created_at, created_by, status)
VALUES ('AUTH', 'http://localhost:25011', 'http://localhost:25011', NULL, NULL, '2025-09-01 10:45:54.220', 'SYS', 'ACT')
ON CONFLICT DO NOTHING;

INSERT INTO public.api_route (group_code, application_id, description, is_public, method, path, rate_limit, rate_limit_duration, created_at, created_by, status, updated_at, updated_by, remark, is_encrypt, priority, start_time, end_time, enable_circuit_breaker)
VALUES ('AUTH', 'LOS', 'Authentication Access Token', 'Y', 'POST', '/oauth/token',  1000, 5, '2025-06-12 18:45:56.733', 'hengkang', 'ACT', '2025-06-12 18:45:56.733', 'SYS', 'PRO', 'N', 1, NULL, NULL, 'N')
ON CONFLICT DO NOTHING;

INSERT INTO public.api_route (group_code, application_id, description, is_public, method, path, rate_limit, rate_limit_duration, created_at, created_by, status, updated_at, updated_by, remark, is_encrypt, priority, start_time, end_time, enable_circuit_breaker)
VALUES ('AUTH', 'LOS', 'Logout', 'Y', 'POST', '/oauth/logout', 1000, 5, '2025-06-12 18:45:56.789', 'hengkang', 'ACT', NULL, NULL, 'DEV', 'N', 1, NULL, NULL, 'N')
ON CONFLICT DO NOTHING;
