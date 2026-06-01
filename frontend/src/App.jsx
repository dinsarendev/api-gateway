import { useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import { ToastProvider } from './context/ToastContext';
import { AuthProvider } from './context/AuthContext';
import Dashboard       from './pages/Dashboard';
import Groups          from './pages/Groups';
import Health          from './pages/Health';
import Registry        from './pages/Registry';
import RoutesPage      from './pages/Routes';
import ApiKeys         from './pages/ApiKeys';
import IpAccessControl from './pages/IpAccessControl';
import OAuth2Providers from './pages/OAuth2Providers';
import Users           from './pages/Users';
import Roles           from './pages/Roles';
import Profile         from './pages/Profile';
import Login           from './pages/Login';
import { auth } from './auth';

const TITLES = {
  '/':                  'Dashboard',
  '/routes':            'Route Management',
  '/groups':            'Service Groups',
  '/registry':          'Service Registry',
  '/health':            'Health Monitor',
  '/security/api-keys': 'API Key Management',
  '/security/ip-acl':   'IP Access Control',
  '/security/oauth2':   'OAuth2 Providers',
  '/users':             'User Management',
  '/roles':             'Role Management',
  '/profile':           'My Profile',
};

// eslint-disable-next-line no-unused-vars
function Layout({ onLogout }) {
  const { pathname } = useLocation();
  const reload = () => window.location.reload();

  return (
    <div id="app">
      <Sidebar />
      <div id="main">
        <header id="topbar">
          <div className="topbar-title">{TITLES[pathname] || 'Admin'}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem' }}>
            <span style={{ fontSize: '.75rem', background: '#dcfce7', color: '#166534', borderRadius: '999px', padding: '.2em .65em', fontWeight: 600 }}>
              <i className="fa-solid fa-circle-check" style={{ marginRight: '.3rem' }} />Gateway Online
            </span>
            <button className="btn btn-secondary btn-sm" onClick={reload}>
              <i className="fa-solid fa-arrows-rotate" /> Refresh
            </button>
          </div>
        </header>
        <div id="content">
          <Routes>
            <Route path="/"                  element={<Dashboard />} />
            <Route path="/routes"            element={<RoutesPage />} />
            <Route path="/groups"            element={<Groups />} />
            <Route path="/registry"          element={<Registry />} />
            <Route path="/health"            element={<Health />} />
            <Route path="/security/api-keys" element={<ApiKeys />} />
            <Route path="/security/ip-acl"   element={<IpAccessControl />} />
            <Route path="/security/oauth2"   element={<OAuth2Providers />} />
            <Route path="/users"             element={<Users />} />
            <Route path="/roles"             element={<Roles />} />
            <Route path="/profile"           element={<Profile />} />
            <Route path="*"                  element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  const [loggedIn, setLoggedIn] = useState(auth.isLoggedIn());

  const handleLogin = () => setLoggedIn(true);

  return (
    <BrowserRouter>
      <ToastProvider>
        {loggedIn
          ? <AuthProvider><Layout /></AuthProvider>
          : <Login onLogin={handleLogin} />
        }
      </ToastProvider>
    </BrowserRouter>
  );
}