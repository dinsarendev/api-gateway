import { NavLink, useNavigate } from 'react-router-dom';
import { auth } from '../auth';
import { API }  from '../api/gateway';
import { useToast } from '../context/ToastContext';

const NavItem = ({ to, icon, label }) => (
  <NavLink to={to} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
    <i className={`fa-solid ${icon}`} />{label}
  </NavLink>
);

export default function Sidebar() {
  const toast    = useToast();
  const navigate = useNavigate();

  const logout = async () => {
    try { await API.logout(); } catch { /* ignore */ }
    auth.clear();
    window.location.reload();
  };

  const username = auth.getUsername();
  const fullName = auth.getFullName();

  return (
    <nav id="sidebar">
      <div className="sidebar-brand">
        <i className="fa-solid fa-network-wired" />
        <span>API Gateway</span>
      </div>

      {/* Logged-in user */}
      <div style={{
        padding: '.75rem 1.2rem', borderBottom: '1px solid #1e293b',
        display: 'flex', alignItems: 'center', gap: '.6rem',
      }}>
        <div style={{
          width: 30, height: 30, borderRadius: '50%',
          background: '#1d4ed8', display: 'flex', alignItems: 'center',
          justifyContent: 'center', fontSize: '.8rem', color: '#fff', fontWeight: 700, flexShrink: 0,
        }}>
          {(fullName || username || '?')[0].toUpperCase()}
        </div>
        <div style={{ overflow: 'hidden' }}>
          <div style={{ fontSize: '.82rem', fontWeight: 600, color: '#f1f5f9',
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {fullName || username}
          </div>
          {fullName && (
            <div style={{ fontSize: '.7rem', color: '#64748b',
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              @{username}
            </div>
          )}
        </div>
      </div>

      <div className="sidebar-section">OVERVIEW</div>
      <NavItem to="/"         icon="fa-gauge-high"   label="Dashboard" />

      <div className="sidebar-section">ROUTING</div>
      <NavItem to="/routes"   icon="fa-route"        label="Routes" />
      <NavItem to="/groups"   icon="fa-layer-group"  label="Service Groups" />

      <div className="sidebar-section">INFRASTRUCTURE</div>
      <NavItem to="/registry" icon="fa-server"       label="Service Registry" />
      <NavItem to="/health"   icon="fa-heart-pulse"  label="Health Monitor" />

      <div className="sidebar-section">SECURITY</div>
      <NavItem to="/security/api-keys" icon="fa-key"           label="API Keys" />
      <NavItem to="/security/ip-acl"   icon="fa-shield-halved" label="IP Access Control" />
      <NavItem to="/security/oauth2"   icon="fa-id-badge"      label="OAuth2 Providers" />

      {/* Logout — pinned to bottom */}
      <div style={{ marginTop: 'auto', padding: '1rem 1.2rem', borderTop: '1px solid #1e293b' }}>
        <button
          onClick={logout}
          style={{
            display: 'flex', alignItems: 'center', gap: '.6rem',
            width: '100%', background: 'none', border: 'none',
            color: '#94a3b8', fontSize: '.875rem', cursor: 'pointer',
            padding: '.4rem 0', borderRadius: 4,
          }}
        >
          <i className="fa-solid fa-right-from-bracket" />
          Sign Out
        </button>
      </div>
    </nav>
  );
}