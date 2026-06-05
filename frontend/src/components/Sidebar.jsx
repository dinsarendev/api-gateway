import { NavLink } from 'react-router-dom';
import { auth } from '../auth';
import { API }  from '../api/gateway';
import { useAuth, PERMS } from '../context/AuthContext';
import { useBrand } from '../context/BrandContext';

const NavItem = ({ to, icon, label, onClick }) => (
  <NavLink
    to={to}
    onClick={onClick}
    className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
  >
    <i className={`fa-solid ${icon}`} />{label}
  </NavLink>
);

export default function Sidebar({ isOpen, collapsed, onClose }) {
  const { can } = useAuth();
  const { brand } = useBrand();

  const logout = async () => {
    try { await API.logout(); } catch { /* ignore */ }
    auth.clear();
    window.location.reload();
  };

  const username = auth.getUsername();
  const fullName = auth.getFullName();
  const initial  = (fullName || username || '?')[0].toUpperCase();

  return (
    <nav id="sidebar" className={[isOpen ? 'open' : '', collapsed ? 'collapsed' : ''].filter(Boolean).join(' ')}>
      <div className="sidebar-brand">
        {brand.brandLogo
          ? <img src={brand.brandLogo} alt="" style={{ height: 22, objectFit: 'contain', flexShrink: 0 }} />
          : <i className="fa-solid fa-network-wired brand-icon" />
        }
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {brand.brandName}
        </span>
        <button className="sidebar-close" onClick={onClose} aria-label="Close menu">
          <i className="fa-solid fa-xmark" />
        </button>
      </div>

      {/* Logged-in user — links to profile page */}
      <NavLink to="/profile" onClick={onClose} style={{ textDecoration: 'none' }}>
        <div
          style={{
            padding: '.75rem 1.2rem', borderBottom: '1px solid #1e293b',
            display: 'flex', alignItems: 'center', gap: '.6rem', cursor: 'pointer',
          }}
          onMouseEnter={e => { e.currentTarget.style.background = '#1e293b'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
        >
          <div style={{
            width: 30, height: 30, borderRadius: '50%',
            background: '#1d4ed8', display: 'flex', alignItems: 'center',
            justifyContent: 'center', fontSize: '.8rem', color: '#fff', fontWeight: 700, flexShrink: 0,
          }}>
            {initial}
          </div>
          <div style={{ overflow: 'hidden', flex: 1 }}>
            <div style={{ fontSize: '.82rem', fontWeight: 600, color: '#f1f5f9', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {fullName || username}
            </div>
            {fullName && (
              <div style={{ fontSize: '.7rem', color: '#64748b', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                @{username}
              </div>
            )}
          </div>
          <i className="fa-solid fa-pen-to-square" style={{ fontSize: '.7rem', color: '#475569', flexShrink: 0 }} />
        </div>
      </NavLink>

      <div className="sidebar-section">OVERVIEW</div>
      <NavItem to="/" icon="fa-gauge-high" label="Dashboard" onClick={onClose} />

      {(can(PERMS.ROUTE_READ) || can(PERMS.GROUP_READ)) && (
        <div className="sidebar-section">ROUTING</div>
      )}
      {can(PERMS.ROUTE_READ) && (
        <NavItem to="/routes"     icon="fa-route"          label="Routes"          onClick={onClose} />
      )}
      {can(PERMS.ROUTE_READ) && (
        <NavItem to="/governance" icon="fa-scale-balanced" label="Governance Docs"  onClick={onClose} />
      )}
      {can(PERMS.GROUP_READ) && (
        <NavItem to="/groups"     icon="fa-layer-group"    label="Service Groups"   onClick={onClose} />
      )}

      {(can(PERMS.INCIDENT_READ) || can(PERMS.MONITORING_READ) || can(PERMS.NOTIFICATION_READ)) && (
        <div className="sidebar-section">MONITORING</div>
      )}
      {can(PERMS.INCIDENT_READ) && (
        <NavItem to="/incidents"      icon="fa-circle-exclamation" label="Incidents"      onClick={onClose} />
      )}
      {can(PERMS.NOTIFICATION_READ) && (
        <NavItem to="/notifications"  icon="fa-bell"               label="Notifications"  onClick={onClose} />
      )}
      {can(PERMS.MONITORING_READ) && (
        <NavItem to="/monitoring"     icon="fa-chart-line"          label="Monitoring"     onClick={onClose} />
      )}

      {(can(PERMS.REGISTRY_READ) || can(PERMS.HEALTH_READ)) && (
        <div className="sidebar-section">INFRASTRUCTURE</div>
      )}
      {can(PERMS.REGISTRY_READ) && (
        <NavItem to="/registry" icon="fa-server" label="Service Registry" onClick={onClose} />
      )}
      {can(PERMS.HEALTH_READ) && (
        <NavItem to="/health" icon="fa-heart-pulse" label="Health Monitor" onClick={onClose} />
      )}

      {can(PERMS.SECURITY_READ) && (
        <>
          <div className="sidebar-section">SECURITY</div>
          <NavItem to="/security/api-keys" icon="fa-key"           label="API Keys"         onClick={onClose} />
          <NavItem to="/security/ip-acl"   icon="fa-shield-halved" label="IP Access Control" onClick={onClose} />
          <NavItem to="/security/oauth2"   icon="fa-id-badge"      label="OAuth2 Providers" onClick={onClose} />
        </>
      )}

      {(can(PERMS.USER_READ) || can(PERMS.AUDIT_LOG_READ)) && (
        <div className="sidebar-section">ADMINISTRATION</div>
      )}
      {can(PERMS.USER_READ) && (
        <>
          <NavItem to="/users" icon="fa-users"       label="Users" onClick={onClose} />
          <NavItem to="/roles" icon="fa-user-shield" label="Roles" onClick={onClose} />
        </>
      )}
      {can(PERMS.AUDIT_LOG_READ) && (
        <NavItem to="/audit-logs" icon="fa-clipboard-list" label="Audit Logs" onClick={onClose} />
      )}

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
