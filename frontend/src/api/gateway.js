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
  login:   (data)  => pub('/admin/auth/login',   { method: 'POST', body: JSON.stringify(data) }),
  refresh: (token) => pub('/admin/auth/refresh', { method: 'POST', body: JSON.stringify({ refresh_token: token }) }),
  logout:  ()      => req('/admin/auth/logout',  { method: 'POST', body: JSON.stringify({ refresh_token: auth.getRefreshToken() }) }),
  profile:        ()     => get('/admin/auth/profile'),
  updateProfile:  (data) => put('/admin/auth/profile', data),
  changePassword: (data) => put('/admin/auth/change-password', data),

  // ── Dashboard ────────────────────────────────────────────────────────────
  dashboard:       () => get('/admin/dashboard'),
  routeStatus:     () => get('/admin/dashboard/route-status'),
  serviceRegistry: () => get('/admin/dashboard/service-registry'),

  // ── Routes ───────────────────────────────────────────────────────────────
  getRoutes:    (status = 'ACT') => get(`/admin/routes?status=${status}`),
  createRoute:  (data)           => post('/admin/routes', data),
  updateRoute:  (id, data)       => put(`/admin/routes/${id}`, data),
  deleteRoute:  (id)             => del(`/admin/routes/${id}`),
  enableRoute:  (id)             => put(`/admin/routes/${id}/enable`),
  disableRoute: (id)             => put(`/admin/routes/${id}/disable`),
  reloadRoutes: ()               => post('/admin/routes/reload'),

  // ── Groups ────────────────────────────────────────────────────────────────
  getGroups:    ()         => get('/admin/groups'),
  createGroup:  (data)     => post('/admin/groups', data),
  updateGroup:  (id, data) => put(`/admin/groups/${id}`, data),
  deleteGroup:  (id)       => del(`/admin/groups/${id}`),

  // ── Health ────────────────────────────────────────────────────────────────
  getInstances:         (svcId = '') => get(`/admin/health/instances${svcId ? '?serviceId=' + svcId : ''}`),
  triggerCheck:         ()           => post('/admin/health/check'),
  updateInstanceStatus: (id, status) => put(`/admin/health/instances/${id}/status`, { healthStatus: status }),

  // ── IP Access Control ─────────────────────────────────────────────────────
  getIpAcl:    ()     => get('/admin/ip-acl'),
  createIpAcl: (data) => post('/admin/ip-acl', data),
  deleteIpAcl: (id)   => del(`/admin/ip-acl/${id}`),

  // ── API Keys ──────────────────────────────────────────────────────────────
  getApiKeys:   ()     => get('/admin/api-keys'),
  createApiKey: (data) => post('/admin/api-keys', data),
  revokeApiKey: (id)   => del(`/admin/api-keys/${id}`),

  // ── OAuth2 Providers ──────────────────────────────────────────────────────
  getOAuth2Providers:   ()         => get('/admin/oauth2-providers'),
  createOAuth2Provider: (data)     => post('/admin/oauth2-providers', data),
  updateOAuth2Provider: (id, data) => put(`/admin/oauth2-providers/${id}`, data),
  deleteOAuth2Provider: (id)       => del(`/admin/oauth2-providers/${id}`),

  // ── Roles ─────────────────────────────────────────────────────────────────
  getRoles:       (params = {}) => {
    const q = new URLSearchParams({ status: 'ACT', search: '', page: 0, size: 15, ...params }).toString();
    return get(`/admin/roles?${q}`);
  },
  getPermissions: ()             => get('/admin/roles/permissions'),
  createRole:     (data)         => post('/admin/roles', data),
  updateRole:     (id, data)     => put(`/admin/roles/${id}`, data),
  updateRoleStatus:(id, status)  => put(`/admin/roles/${id}/status`, { status }),

  // ── Users ─────────────────────────────────────────────────────────────────
  getUsers:       (params = {}) => {
    const q = new URLSearchParams({ status: 'ACT', search: '', page: 0, size: 15, ...params }).toString();
    return get(`/admin/users?${q}`);
  },
  createUser:     (data)     => post('/admin/users', data),
  updateUser:     (id, data) => put(`/admin/users/${id}`, data),
  updateUserStatus:(id, status) => put(`/admin/users/${id}/status`, { status }),
  deleteUser:     (id)       => del(`/admin/users/${id}`),
};