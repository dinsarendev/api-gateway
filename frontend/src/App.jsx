import { HashRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import { ToastProvider } from './context/ToastContext';
import Dashboard from './pages/Dashboard';
import Groups    from './pages/Groups';
import Health    from './pages/Health';
import Registry  from './pages/Registry';
import RoutesPage from './pages/Routes';
import { API } from './api/gateway';

const TITLES = {
  '/':         'Dashboard',
  '/routes':   'Route Management',
  '/groups':   'Service Groups',
  '/registry': 'Service Registry',
  '/health':   'Health Monitor',
};

function Layout() {
  const { pathname } = useLocation();

  const reload = () => window.location.reload();
  const reloadGateway = async () => {
    try { await API.reloadRoutes(); } catch { /* already handled in page */ }
  };

  return (
    <div id="app">
      <Sidebar />
      <div id="main">
        <header id="topbar">
          <div className="topbar-title">{TITLES[pathname] || 'Admin'}</div>
          <div style={{ display:'flex', alignItems:'center', gap:'.5rem' }}>
            <span style={{ fontSize:'.75rem', background:'#dcfce7', color:'#166534', borderRadius:'999px', padding:'.2em .65em', fontWeight:600 }}>
              <i className="fa-solid fa-circle-check" style={{marginRight:'.3rem'}} />Gateway Online
            </span>
            <button className="btn btn-secondary btn-sm" onClick={reload}>
              <i className="fa-solid fa-arrows-rotate" /> Refresh
            </button>
          </div>
        </header>
        <div id="content">
          <Routes>
            <Route path="/"         element={<Dashboard />} />
            <Route path="/routes"   element={<RoutesPage />} />
            <Route path="/groups"   element={<Groups />} />
            <Route path="/registry" element={<Registry />} />
            <Route path="/health"   element={<Health />} />
            <Route path="*"         element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <HashRouter>
      <ToastProvider>
        <Layout />
      </ToastProvider>
    </HashRouter>
  );
}
