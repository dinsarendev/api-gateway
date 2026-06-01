import { useEffect, useState } from 'react';
import { API } from '../api/gateway';
import { useToast } from '../context/ToastContext';

const StatCard = ({ icon, color, value, label }) => (
  <div className="stat-card">
    <div className={`stat-icon ${color}`}><i className={`fa-solid ${icon}`} /></div>
    <div>
      <div className="stat-value">{value ?? '—'}</div>
      <div className="stat-label">{label}</div>
    </div>
  </div>
);

const MethodBadge = ({ m }) => <span className={`method-badge method-${m}`}>{m}</span>;

export default function Dashboard() {
  const toast = useToast();
  const [summary,  setSummary]  = useState(null);
  const [routeSt,  setRouteSt]  = useState(null);
  const [registry, setRegistry] = useState(null);
  const [loading,  setLoading]  = useState(true);

  useEffect(() => {
    Promise.all([API.dashboard(), API.routeStatus(), API.serviceRegistry()])
      .then(([s, r, reg]) => { setSummary(s); setRouteSt(r); setRegistry(reg); })
      .catch(() => toast.error('Failed to load dashboard'))
      .finally(() => setLoading(false));
  }, []);

  const instances = Object.values(summary?.serviceInstances || {});
  const totalUp   = instances.reduce((a, i) => a + (i.up   || 0), 0);
  const totalInst = instances.reduce((a, i) => a + (i.total || 0), 0);

  return (
    <div>
      <div className="stats-grid">
        <StatCard icon="fa-route"        color="blue"   value={summary?.routes?.total}  label="Total Routes" />
        <StatCard icon="fa-circle-check" color="green"  value={summary?.routes?.active} label="Active Routes" />
        <StatCard icon="fa-layer-group"  color="purple" value={summary?.serviceGroups}  label="Service Groups" />
        <StatCard icon="fa-server"       color={totalUp < totalInst ? 'amber' : 'green'}
                  value={totalInst ? `${totalUp}/${totalInst}` : '—'} label="Instances UP" />
      </div>

      <div className="grid-2">
        {/* Routes by group */}
        <div className="card">
          <div className="card-header">Routes by Service Group</div>
          <div className="table-wrap">
            {loading ? <div className="loading-center"><div className="spinner" /></div> : (
              <table>
                <thead><tr><th>Group</th><th>Routes</th><th>Methods</th></tr></thead>
                <tbody>
                  {Object.entries(routeSt?.groups || {}).map(([g, v]) => (
                    <tr key={g}>
                      <td className="fw-bold">{g}</td>
                      <td><span style={{ background:'#eff6ff',color:'#1d4ed8',borderRadius:'999px',padding:'.15em .55em',fontSize:'.78rem',fontWeight:600 }}>{v.count}</span></td>
                      <td>{(v.methods||[]).map(m => <MethodBadge key={m} m={m} />).reduce((a,b)=>[a,' ',b],null)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* Service registry */}
        <div className="card">
          <div className="card-header">Service Registry</div>
          <div className="table-wrap">
            {loading ? <div className="loading-center"><div className="spinner" /></div> : (
              <table>
                <thead><tr><th>Service</th><th>URI</th><th>Health</th></tr></thead>
                <tbody>
                  {(registry || []).map(g => (
                    <tr key={g.code}>
                      <td className="fw-bold">{g.code}</td>
                      <td className="font-mono" style={{ fontSize: '.78rem' }}>
                        {g.lbEnabled && <span className="lb-badge">lb://</span>}{' '}
                        {g.uri}
                      </td>
                      <td>
                        <span className={`badge badge-${g.up > 0 && g.up === g.instances ? 'up' : g.instances === 0 ? 'oos' : 'down'}`}>
                          {g.up}/{g.instances}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
