const handle = async (res) => {
  const body = await res.json();
  if (!res.ok) throw body;
  return body;
};

const req = (url, opts = {}) =>
  fetch(url, { headers: { 'Content-Type': 'application/json' }, ...opts }).then(handle);

const get    = url        => req(url);
const post   = (url, d)  => req(url, { method: 'POST',   body: JSON.stringify(d) });
const put    = (url, d)  => req(url, { method: 'PUT',    body: JSON.stringify(d ?? {}) });
const del    = url        => req(url, { method: 'DELETE' });

export const API = {
  // Dashboard
  dashboard:       () => get('/admin/dashboard'),
  routeStatus:     () => get('/admin/dashboard/route-status'),
  serviceRegistry: () => get('/admin/dashboard/service-registry'),

  // Routes
  getRoutes:    (status = 'ACT') => get(`/admin/routes?status=${status}`),
  createRoute:  (data)           => post('/admin/routes', data),
  updateRoute:  (id, data)       => put(`/admin/routes/${id}`, data),
  deleteRoute:  (id)             => del(`/admin/routes/${id}`),
  enableRoute:  (id)             => put(`/admin/routes/${id}/enable`),
  disableRoute: (id)             => put(`/admin/routes/${id}/disable`),
  reloadRoutes: ()               => post('/admin/routes/reload'),

  // Groups
  getGroups:    ()          => get('/admin/groups'),
  createGroup:  (data)      => post('/admin/groups', data),
  updateGroup:  (id, data)  => put(`/admin/groups/${id}`, data),
  deleteGroup:  (id)        => del(`/admin/groups/${id}`),

  // Health
  getInstances:         (svcId = '') => get(`/admin/health/instances${svcId ? '?serviceId=' + svcId : ''}`),
  triggerCheck:         ()           => post('/admin/health/check'),
  updateInstanceStatus: (id, status) => put(`/admin/health/instances/${id}/status`, { healthStatus: status }),
};
