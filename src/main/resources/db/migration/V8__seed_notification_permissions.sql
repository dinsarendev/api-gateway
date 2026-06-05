-- ── New permissions introduced with alert notification feature ─────────────────
INSERT INTO public.admin_permission (name, description, status, created_at, created_by)
VALUES
    ('NOTIFICATION_READ',  'View notification channels',                  'ACT', NOW(), 'SYS'),
    ('NOTIFICATION_WRITE', 'Create / update / delete notification channels', 'ACT', NOW(), 'SYS')
ON CONFLICT (name) DO NOTHING;

-- ── SUPER_ADMIN — grant both new permissions ───────────────────────────────────
INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM   public.admin_role r
       JOIN public.admin_permission p
         ON p.name IN ('NOTIFICATION_READ', 'NOTIFICATION_WRITE')
WHERE  r.name = 'SUPER_ADMIN'
  AND  p.status = 'ACT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ── OPERATOR — can manage notification channels ────────────────────────────────
INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM   public.admin_role r
       JOIN public.admin_permission p
         ON p.name IN ('NOTIFICATION_READ', 'NOTIFICATION_WRITE')
WHERE  r.name = 'OPERATOR'
  AND  p.status = 'ACT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ── VIEWER — read-only access to notification channels ────────────────────────
INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM   public.admin_role r
       JOIN public.admin_permission p
         ON p.name = 'NOTIFICATION_READ'
WHERE  r.name = 'VIEWER'
  AND  p.status = 'ACT'
ON CONFLICT (role_id, permission_id) DO NOTHING;
