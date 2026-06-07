import { useEffect, useState } from 'react';
import { API } from '../api/gateway';
import { useToast } from '../context/ToastContext';

const StatCard = ({ icon, color, value, label, sub }) => (
  <div className="stat-card">
    <div className={`stat-icon ${color}`}><i className={`fa-solid ${icon}`} /></div>
    <div>
      <div className="stat-value">{value ?? '—'}</div>
      <div className="stat-label">{label}</div>
      {sub && <div className="text-muted" style={{ fontSize: '.7rem', marginTop: '.1rem' }}>{sub}</div>}
    </div>
  </div>
);

const MethodBadge = ({ m }) => <span className={`method-badge method-${m}`}>{m}</span>;

const API_TYPE_META = {
  REST:      { icon: 'fa-network-wired',  color: '#3b82f6' },
  SOAP:      { icon: 'fa-code',           color: '#8b5cf6' },
  GRAPHQL:   { icon: 'fa-diagram-project', color: '#e11d48' },
  STREAMING: { icon: 'fa-wave-square',    color: '#0891b2' },
  AI:        { icon: 'fa-robot',          color: '#059669' },
};

function ApiTypeBar({ breakdown }) {
  if (!breakdown || Object.keys(breakdown).length === 0) return null;
  const total = Object.values(breakdown).reduce((a, b) => a + b, 0);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '.5rem' }}>
      {Object.entries(breakdown)
        .sort((a, b) => b[1] - a[1])
        .map(([type, count]) => {
          const meta = API_TYPE_META[type] || { icon: 'fa-circle', color: '#94a3b8' };
          const pct  = total > 0 ? Math.round((count / total) * 100) : 0;
          return (
            <div key={type}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '.78rem', marginBottom: '.2rem' }}>
                <span style={{ color: meta.color, fontWeight: 600 }}>
                  <i className={`fa-solid ${meta.icon}`} style={{ marginRight: '.35rem' }} />{type}
                </span>
                <span className="text-muted">{count} route{count !== 1 ? 's' : ''} ({pct}%)</span>
              </div>
              <div style={{ height: 6, borderRadius: 4, background: '#f1f5f9', overflow: 'hidden' }}>
                <div style={{ width: `${pct}%`, height: '100%', background: meta.color, borderRadius: 4, transition: 'width .4s' }} />
              </div>
            </div>
          );
        })}
    </div>
  );
}

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

  const inst    = summary?.instancesSummary || {};
  const instUp  = inst.up   ?? 0;
  const instAll = inst.total ?? 0;

  return (
    <div>
      {/* ── Stat cards ─────────────────────────────────────────────────── */}
      <div className="stats-grid">
        <StatCard icon="fa-route"        color="blue"
          value={summary?.routes?.total}
          label="Total Routes"
          sub={`${summary?.routes?.active ?? '—'} active · ${summary?.routes?.inactive ?? '—'} inactive`} />

        <StatCard icon="fa-layer-group"  color="purple"
          value={summary?.serviceGroups}
          label="Service Groups" />

        <StatCard icon="fa-server"
          color={instAll === 0 ? 'gray' : instUp < instAll ? 'amber' : 'green'}
          value={instAll ? `${instUp}/${instAll}` : '—'}
          label="Instances UP"
          sub={instAll > 0 && instUp < instAll ? `${instAll - instUp} DOWN` : undefined} />

        <StatCard icon="fa-key"          color="amber"
          value={summary?.apiKeys}
          label="Active API Keys" />

        <StatCard icon="fa-users"        color="blue"
          value={summary?.adminUsers}
          label="Admin Users" />
      </div>

      {/* ── Main grid ──────────────────────────────────────────────────── */}
      <div className="grid-2">

        {/* Routes by group */}
        <div className="card">
          <div className="card-header">Routes by Service Group</div>
          <div className="table-wrap">
            {loading ? <div className="loading-center"><div className="spinner" /></div> : (
              <table>
                <thead>
                  <tr><th>Group</th><th>Routes</th><th>Methods</th><th>Types</th></tr>
                </thead>
                <tbody>
                  {Object.entries(routeSt?.groups || {}).map(([g, v]) => (
                    <tr key={g}>
                      <td className="fw-bold">{g}</td>
                      <td>
                        <span style={{ background:'#eff6ff', color:'#1d4ed8', borderRadius:'999px',
                          padding:'.15em .55em', fontSize:'.78rem', fontWeight: 600 }}>
                          {v.count}
                        </span>
                      </td>
                      <td>
                        {(v.methods || []).map(m => <MethodBadge key={m} m={m} />)
                          .reduce((a, b) => [a, ' ', b], null)}
                      </td>
                      <td>
                        {Object.entries(v.apiTypes || {}).map(([type, cnt]) => {
                          const meta = API_TYPE_META[type] || { icon: 'fa-circle', color: '#94a3b8' };
                          return (
                            <span key={type} title={`${type}: ${cnt}`} style={{
                              display: 'inline-flex', alignItems: 'center', gap: '.2rem',
                              fontSize: '.7rem', fontWeight: 600, color: meta.color,
                              background: meta.color + '18', padding: '2px 6px',
                              borderRadius: 4, marginRight: '.25rem'
                            }}>
                              <i className={`fa-solid ${meta.icon}`} />{cnt}
                            </span>
                          );
                        })}
                      </td>
                    </tr>
                  ))}
                  {!loading && Object.keys(routeSt?.groups || {}).length === 0 && (
                    <tr><td colSpan={4} className="text-muted text-sm" style={{ textAlign: 'center', padding: '1rem' }}>
                      No active routes
                    </td></tr>
                  )}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* Right column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>

          {/* API Type distribution */}
          <div className="card">
            <div className="card-header">
              <span>API Type Distribution</span>
              <span className="text-muted text-sm">{summary?.routes?.active ?? 0} active routes</span>
            </div>
            <div style={{ padding: '1rem' }}>
              {loading
                ? <div className="loading-center"><div className="spinner" /></div>
                : <ApiTypeBar breakdown={summary?.apiTypeBreakdown} />}
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
                        <td className="font-mono" style={{ fontSize: '.75rem' }}>
                          {g.lbEnabled && <span className="lb-badge">lb://</span>}{' '}
                          {g.uri}
                        </td>
                        <td>
                          <span className={`badge badge-${
                            g.instances === 0 ? 'oos' :
                            g.up > 0 && g.up === g.instances ? 'up' : 'down'
                          }`}>
                            {g.up}/{g.instances}
                          </span>
                        </td>
                      </tr>
                    ))}
                    {!loading && (registry || []).length === 0 && (
                      <tr><td colSpan={3} className="text-muted text-sm" style={{ textAlign:'center', padding:'1rem' }}>
                        No service groups
                      </td></tr>
                    )}
                  </tbody>
                </table>
              )}
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
