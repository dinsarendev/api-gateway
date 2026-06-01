import { createContext, useContext, useMemo } from 'react';
import { auth } from '../auth';

const AuthContext = createContext(null);

// Permission → menu/action mapping
export const PERMS = {
  ROUTE_READ:    'ROUTE_READ',
  ROUTE_WRITE:   'ROUTE_WRITE',
  GROUP_READ:    'GROUP_READ',
  GROUP_WRITE:   'GROUP_WRITE',
  REGISTRY_READ: 'REGISTRY_READ',
  REGISTRY_WRITE:'REGISTRY_WRITE',
  HEALTH_READ:   'HEALTH_READ',
  SECURITY_READ: 'SECURITY_READ',
  SECURITY_WRITE:'SECURITY_WRITE',
  USER_READ:     'USER_READ',
  USER_WRITE:    'USER_WRITE',
};

export function AuthProvider({ children }) {
  const value = useMemo(() => {
    const roles       = auth.getRoles();
    const permissions = auth.getPermissions();
    const permSet     = new Set(permissions);
    const roleSet     = new Set(roles);

    return {
      roles,
      permissions,
      can:          (perm)  => permSet.has(perm),
      hasRole:      (role)  => roleSet.has(role),
      isSuperAdmin: ()      => roleSet.has('SUPER_ADMIN'),
    };
  }, []);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}