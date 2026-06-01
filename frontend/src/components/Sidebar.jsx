import { NavLink } from 'react-router-dom';

const NavItem = ({ to, icon, label }) => (
  <NavLink to={to} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
    <i className={`fa-solid ${icon}`} />{label}
  </NavLink>
);

export default function Sidebar() {
  return (
    <nav id="sidebar">
      <div className="sidebar-brand">
        <i className="fa-solid fa-network-wired" />
        <span>API Gateway</span>
      </div>

      <div className="sidebar-section">OVERVIEW</div>
      <NavItem to="/"         icon="fa-gauge-high"   label="Dashboard" />

      <div className="sidebar-section">ROUTING</div>
      <NavItem to="/routes"   icon="fa-route"        label="Routes" />
      <NavItem to="/groups"   icon="fa-layer-group"  label="Service Groups" />

      <div className="sidebar-section">INFRASTRUCTURE</div>
      <NavItem to="/registry" icon="fa-server"       label="Service Registry" />
      <NavItem to="/health"   icon="fa-heart-pulse"  label="Health Monitor" />
    </nav>
  );
}
