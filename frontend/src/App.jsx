import { useState, useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import { ToastProvider } from './context/ToastContext';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider, useTheme } from './context/ThemeContext';
import { BrandProvider } from './context/BrandContext';
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
import Incidents       from './pages/Incidents';
import Monitoring      from './pages/Monitoring';
import Profile         from './pages/Profile';
import Login           from './pages/Login';
import { auth } from './auth';

const TITLES = {
  '/':                  'Dashboard',
  '/routes':            'Route Management',
  '/groups':            'Service Groups',
  '/registry':          'Service Registry',
  '/health':            'Health Monitor',
  '/incidents':         'Incidents',
  '/monitoring':        'Monitoring',
  '/security/api-keys': 'API Key Management',
  '/security/ip-acl':   'IP Access Control',
  '/security/oauth2':   'OAuth2 Providers',
  '/users':             'User Management',
  '/roles':             'Role Management',
  '/profile':           'My Profile',
};

function Layout({ sidebarOpen, setSidebarOpen }) {
  const { pathname } = useLocation();
  const { theme, toggle: toggleTheme } = useTheme();
  const reload = () => window.location.reload();

  useEffect(() => {
    const page = TITLES[pathname] || 'Admin';
    document.title = `API Gateway — ${page}`;
  }, [pathname]);

  return (
    <div id="app">
      {/* Mobile backdrop */}
      <div
        className={`sidebar-overlay${sidebarOpen ? ' open' : ''}`}
        onClick={() => setSidebarOpen(false)}
      />

      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div id="main">
        <header id="topbar">
          <div className="topbar-left">
            <button className="btn-hamburger" onClick={() => setSidebarOpen(s => !s)} aria-label="Toggle menu">
              <i className="fa-solid fa-bars" />
            </button>
            <div className="topbar-title">{TITLES[pathname] || 'Admin'}</div>
          </div>

          <div className="topbar-right">
            <span
              className="gateway-badge"
              style={{ fontSize: '.75rem', background: '#dcfce7', color: '#166534', borderRadius: '999px', padding: '.2em .65em', fontWeight: 600, whiteSpace: 'nowrap' }}
            >
              <i className="fa-solid fa-circle-check" style={{ marginRight: '.3rem' }} />Gateway Online
            </span>

            <button
              className="btn btn-secondary btn-sm"
              onClick={toggleTheme}
              title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              <i className={`fa-solid ${theme === 'dark' ? 'fa-sun' : 'fa-moon'}`} />
            </button>

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
            <Route path="/incidents"          element={<Incidents />} />
            <Route path="/monitoring"         element={<Monitoring />} />
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
  const [loggedIn, setLoggedIn]       = useState(auth.isLoggedIn());
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleLogin = () => setLoggedIn(true);

  return (
    <BrowserRouter>
      <ThemeProvider>
        <BrandProvider>
          <ToastProvider>
            {loggedIn
              ? (
                <AuthProvider>
                  <Layout sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />
                </AuthProvider>
              )
              : <Login onLogin={handleLogin} />
            }
          </ToastProvider>
        </BrandProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
