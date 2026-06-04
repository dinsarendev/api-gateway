import { auth } from '../auth';

const handle = async (res) => {
  if (res.status === 401) {
    auth.clear();
    window.location.reload();
    throw new Error('Unauthorized');
  }
  const body = await res.json();
  if (!res.ok) throw body;
  return body;
};

// Authenticated request — auto-refreshes token if needed
const req = async (url, opts = {}) => {
  const token = await auth.getValidToken();
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return fetch(url, { headers, ...opts }).then(handle);
};

// Unauthenticated request (login / refresh)
const pub = (url, opts = {}) =>
  fetch(url, { headers: { 'Content-Type': 'application/json' }, ...opts }).then(handle);

const get  = url       => req(url);
const post = (url, d)  => req(url, { method: 'POST',   body: JSON.stringify(d) });
const put  = (url, d)  => req(url, { method: 'PUT',    body: JSON.stringify(d ?? {}) });
const del  = url       => req(url, { method: 'DELETE' });

export const API = {
  // ── Auth (no token needed) ───────────────────────────────────────────────
  login:   (data)  => pub('/api/management/admin/auth/login',   { method: 'POST', body: JSON.stringify(data) }),
  refresh: (token) => pub('/api/management/admin/auth/refresh', { method: 'POST', body: JSON.stringify({ refresh_token: token }) }),
  logout:  ()      => req('/api/management/admin/auth/logout',  { method: 'POST', body: JSON.stringify({ refresh_token: auth.getRefreshToken() }) }),
  profile:        ()     => get('/api/management/admin/auth/profile'),
  updateProfile:  (data) => put('/api/management/admin/auth/profile', data),
  changePassword: (data) => put('/api/management/admin/auth/change-password', data),

  // ── Dashboard ────────────────────────────────────────────────────────────
  dashboard:       () => get('/api/management/admin/dashboard'),
  routeStatus:     () => get('/api/management/admin/dashboard/route-status'),
  serviceRegistry: () => get('/api/management/admin/dashboard/service-registry'),

  // ── Routes ───────────────────────────────────────────────────────────────
  getRoutes:    (status = 'ACT') => get(`/api/management/admin/routes?status=${status}`),
  createRoute:  (data)           => post('/api/management/admin/routes', data),
  updateRoute:  (id, data)       => put(`/api/management/admin/routes/${id}`, data),
  deleteRoute:  (id)             => del(`/api/management/admin/routes/${id}`),
  enableRoute:  (id)             => put(`/api/management/admin/routes/${id}/enable`),
  disableRoute: (id)             => put(`/api/management/admin/routes/${id}/disable`),
  reloadRoutes: ()               => post('/api/management/admin/routes/reload'),

  // ── Groups ────────────────────────────────────────────────────────────────
  getGroups:    ()         => get('/api/management/admin/groups'),
  createGroup:  (data)     => post('/api/management/admin/groups', data),
  updateGroup:  (id, data) => put(`/api/management/admin/groups/${id}`, data),
  deleteGroup:  (id)       => del(`/api/management/admin/groups/${id}`),

  // ── Blue-Green deployment ─────────────────────────────────────────────────
  getBlueGreen:       (code)       => get(`/api/management/admin/groups/${code}/blue-green`),
  configureBlueGreen: (code, data) => put(`/api/management/admin/groups/${code}/blue-green`, data),
  swapSlot:           (code)       => post(`/api/management/admin/groups/${code}/swap`),

  // ── Health / Service Registry ─────────────────────────────────────────────
  getInstances:         (svcId = '') => get(`/api/management/admin/health/instances${svcId ? '?serviceId=' + svcId : ''}`),
  createInstance:       (data)       => post('/api/management/admin/health/instances', data),
  updateInstance:       (id, data)   => put(`/api/management/admin/health/instances/${id}`, data),
  deleteInstance:       (id)         => del(`/api/management/admin/health/instances/${id}`),
  triggerCheck:         ()           => post('/api/management/admin/health/check'),
  updateInstanceStatus: (id, status) => put(`/api/management/admin/health/instances/${id}/status`, { healthStatus: status }),

  // ── IP Access Control ─────────────────────────────────────────────────────
  getIpAcl:    ()     => get('/api/management/admin/ip-acl'),
  createIpAcl: (data) => post('/api/management/admin/ip-acl', data),
  deleteIpAcl: (id)   => del(`/api/management/admin/ip-acl/${id}`),

  // ── API Keys ──────────────────────────────────────────────────────────────
  getApiKeys:   ()     => get('/api/management/admin/api-keys'),
  createApiKey: (data) => post('/api/management/admin/api-keys', data),
  revokeApiKey: (id)   => del(`/api/management/admin/api-keys/${id}`),

  // ── OAuth2 Providers ──────────────────────────────────────────────────────
  getOAuth2Providers:   ()         => get('/api/management/admin/oauth2-providers'),
  createOAuth2Provider: (data)     => post('/api/management/admin/oauth2-providers', data),
  updateOAuth2Provider: (id, data) => put(`/api/management/admin/oauth2-providers/${id}`, data),
  deleteOAuth2Provider: (id)       => del(`/api/management/admin/oauth2-providers/${id}`),

  // ── Incidents ─────────────────────────────────────────────────────────────
  getIncidentDashboard: ()           => get('/api/management/admin/incidents/dashboard'),
  getIncidents:         (status = 'ALL') => get(`/api/management/admin/incidents?status=${status}`),
  getIncident:          (id)         => get(`/api/management/admin/incidents/${id}`),
  createIncident:       (data)       => post('/api/management/admin/incidents', data),
  updateIncident:       (id, data)   => put(`/api/management/admin/incidents/${id}`, data),
  resolveIncident:      (id)         => post(`/api/management/admin/incidents/${id}/resolve`),
  closeIncident:        (id)         => del(`/api/management/admin/incidents/${id}`),

  // ── Monitoring ────────────────────────────────────────────────────────────
  getGatewayMetrics:  (window = 60) => get(`/api/management/admin/metrics/gateway?window=${window}`),
  getApiMetrics:      (window = 1, limit = 10) => get(`/api/management/admin/metrics/apis?window=${window}&limit=${limit}`),
  getConsumerMetrics: (window = 1, limit = 10) => get(`/api/management/admin/metrics/consumers?window=${window}&limit=${limit}`),

  // ── Audit Logs ────────────────────────────────────────────────────────────
  getAuditLogs: (params = {}) => {
    const q = new URLSearchParams({ page: 0, size: 20, ...params }).toString();
    return get(`/api/management/admin/audit-logs?${q}`);
  },

  // ── Roles ─────────────────────────────────────────────────────────────────
  getRoles:       (params = {}) => {
    const q = new URLSearchParams({ status: 'ACT', search: '', page: 0, size: 15, ...params }).toString();
    return get(`/api/management/admin/roles?${q}`);
  },
  getPermissions: ()             => get('/api/management/admin/roles/permissions'),
  createRole:     (data)         => post('/api/management/admin/roles', data),
  updateRole:     (id, data)     => put(`/api/management/admin/roles/${id}`, data),
  updateRoleStatus:(id, status)  => put(`/api/management/admin/roles/${id}/status`, { status }),

  // ── Users ─────────────────────────────────────────────────────────────────
  getUsers:       (params = {}) => {
    const q = new URLSearchParams({ status: 'ACT', search: '', page: 0, size: 15, ...params }).toString();
    return get(`/api/management/admin/users?${q}`);
  },
  createUser:     (data)     => post('/api/management/admin/users', data),
  updateUser:     (id, data) => put(`/api/management/admin/users/${id}`, data),
  updateUserStatus:(id, status) => put(`/api/management/admin/users/${id}/status`, { status }),
  deleteUser:     (id)       => del(`/api/management/admin/users/${id}`),
};