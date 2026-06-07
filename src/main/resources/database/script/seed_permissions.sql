-- ============================================================
-- Permission seed — run manually on existing databases.
-- The application seeder (AdminSeeder.java) executes this
-- logic automatically on every startup via upsertPermission().
-- Use this script when you need to apply changes without
-- restarting the application.
-- ============================================================

-- ── 1. Upsert all permissions ────────────────────────────────
INSERT INTO public.admin_permission (name, description, status, created_at, created_by)
VALUES
    ('ROUTE_READ',      'View routes',                                           'ACT', NOW(), 'SYS'),
    ('ROUTE_WRITE',     'Create / update / delete routes',                       'ACT', NOW(), 'SYS'),
    ('GROUP_READ',      'View service groups',                                   'ACT', NOW(), 'SYS'),
    ('GROUP_WRITE',     'Create / update / delete groups',                       'ACT', NOW(), 'SYS'),
    ('REGISTRY_READ',   'View service registry',                                 'ACT', NOW(), 'SYS'),
    ('REGISTRY_WRITE',  'Manage service instances',                              'ACT', NOW(), 'SYS'),
    ('HEALTH_READ',     'View health monitor',                                   'ACT', NOW(), 'SYS'),
    ('SECURITY_READ',   'View security settings',                                'ACT', NOW(), 'SYS'),
    ('SECURITY_WRITE',  'Manage API keys, IP rules, OAuth2 providers',           'ACT', NOW(), 'SYS'),
    ('USER_READ',       'View admin users',                                      'ACT', NOW(), 'SYS'),
    ('USER_WRITE',      'Create / update / delete admin users',                  'ACT', NOW(), 'SYS'),
    ('ROLE_READ',       'View roles and permissions',                            'ACT', NOW(), 'SYS'),
    ('ROLE_WRITE',      'Create / update / delete roles and assign permissions', 'ACT', NOW(), 'SYS'),
    ('MONITORING_READ', 'View live gateway metrics and monitoring dashboard',     'ACT', NOW(), 'SYS'),
    ('INCIDENT_READ',   'View incidents and incident dashboard',                  'ACT', NOW(), 'SYS'),
    ('INCIDENT_WRITE',  'Create, update, resolve and close incidents',            'ACT', NOW(), 'SYS'),
    ('AUDIT_LOG_READ',  'View audit logs',                                        'ACT', NOW(), 'SYS')
ON CONFLICT (name) DO NOTHING;

-- ── 2. Upsert roles ──────────────────────────────────────────
INSERT INTO public.admin_role (name, description, status, created_at, created_by)
VALUES
    ('SUPER_ADMIN', 'Full access to all admin functions',                          'ACT', NOW(), 'SYS'),
    ('OPERATOR',    'Manage routes and groups, read-only on security',             'ACT', NOW(), 'SYS'),
    ('VIEWER',      'Read-only access',                                            'ACT', NOW(), 'SYS')
ON CONFLICT (name) DO NOTHING;

-- ── 3. Assign ALL permissions to SUPER_ADMIN ─────────────────
INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM   public.admin_role r
       CROSS JOIN public.admin_permission p
WHERE  r.name = 'SUPER_ADMIN'
  AND  p.status = 'ACT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ── 4. OPERATOR permissions ───────────────────────────────────
INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM   public.admin_role r
       JOIN public.admin_permission p
         ON p.name IN (
             'ROUTE_READ',     'ROUTE_WRITE',
             'GROUP_READ',     'GROUP_WRITE',
             'REGISTRY_READ',
             'HEALTH_READ',
             'SECURITY_READ',
             'ROLE_READ',
             'MONITORING_READ',
             'INCIDENT_READ',  'INCIDENT_WRITE',
             'AUDIT_LOG_READ'
         )
WHERE  r.name = 'OPERATOR'
  AND  p.status = 'ACT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ── 5. VIEWER permissions ─────────────────────────────────────
INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM   public.admin_role r
       JOIN public.admin_permission p
         ON p.name IN (
             'ROUTE_READ',
             'GROUP_READ',
             'REGISTRY_READ',
             'HEALTH_READ',
             'SECURITY_READ',
             'ROLE_READ',
             'USER_READ',
             'MONITORING_READ',
             'INCIDENT_READ',
             'AUDIT_LOG_READ'
         )
WHERE  r.name = 'VIEWER'
  AND  p.status = 'ACT'
ON CONFLICT (role_id, permission_id) DO NOTHING;
