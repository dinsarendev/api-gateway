const K = {
  ACCESS:       'gw_access_token',
  REFRESH:      'gw_refresh_token',
  EXPIRES_AT:   'gw_expires_at',
  USERNAME:     'gw_username',
  FULL_NAME:    'gw_full_name',
  ROLES:        'gw_roles',
  PERMISSIONS:  'gw_permissions',
};

let refreshPromise = null; // mutex — prevents concurrent refresh calls

export const auth = {
  // ── Getters ─────────────────────────────────────────────────────────────────
  getAccessToken:  () => localStorage.getItem(K.ACCESS),
  getRefreshToken: () => localStorage.getItem(K.REFRESH),
  getUsername:     () => localStorage.getItem(K.USERNAME) || '',
  getFullName:     () => localStorage.getItem(K.FULL_NAME) || '',
  getRoles:        () => JSON.parse(localStorage.getItem(K.ROLES)       || '[]'),
  getPermissions:  () => JSON.parse(localStorage.getItem(K.PERMISSIONS) || '[]'),
  isLoggedIn:      () => !!localStorage.getItem(K.ACCESS),

  // ── Store after login / refresh ─────────────────────────────────────────────
  setTokens({ access_token, refresh_token, expires_in, username, full_name, roles, permissions }) {
    localStorage.setItem(K.ACCESS,    access_token);
    localStorage.setItem(K.REFRESH,   refresh_token);
    localStorage.setItem(K.EXPIRES_AT, String(Date.now() + expires_in * 1000));
    if (username)     localStorage.setItem(K.USERNAME,     username);
    if (full_name)    localStorage.setItem(K.FULL_NAME,    full_name);
    if (roles)        localStorage.setItem(K.ROLES,        JSON.stringify(roles));
    if (permissions)  localStorage.setItem(K.PERMISSIONS,  JSON.stringify(permissions));
  },

  clear() {
    Object.values(K).forEach(k => localStorage.removeItem(k));
  },

  // ── Returns a valid access token, refreshing silently if about to expire ────
  async getValidToken() {
    if (!auth.isLoggedIn()) return null;

    const expiresAt = parseInt(localStorage.getItem(K.EXPIRES_AT) || '0');
    const needsRefresh = Date.now() > expiresAt - 60_000; // refresh 60s before expiry

    if (!needsRefresh) return auth.getAccessToken();

    // Deduplicate concurrent refresh calls
    if (!refreshPromise) {
      refreshPromise = doRefresh().finally(() => { refreshPromise = null; });
    }
    return refreshPromise;
  },
};

async function doRefresh() {
  const refreshToken = auth.getRefreshToken();
  if (!refreshToken) { auth.clear(); return null; }

  try {
    const res = await fetch('/admin/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });

    if (!res.ok) { auth.clear(); return null; }

    const data = await res.json();
    auth.setTokens(data);
    return data.access_token;
  } catch {
    auth.clear();
    return null;
  }
}