import { useCallback, useEffect, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

// ── Constants ──────────────────────────────────────────────────────────────────

const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const TYPES      = ['AVAILABILITY', 'PERFORMANCE', 'ERROR', 'INFRASTRUCTURE'];
const STATUSES   = ['OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED'];

const SEV_COLOR = {
  CRITICAL: { bg: '#fef2f2', color: '#991b1b', dot: '#ef4444' },
  HIGH:     { bg: '#fff7ed', color: '#9a3412', dot: '#f97316' },
  MEDIUM:   { bg: '#fefce8', color: '#854d0e', dot: '#eab308' },
  LOW:      { bg: '#f0fdf4', color: '#166534', dot: '#22c55e' },
};

const STATUS_COLOR = {
  OPEN:          { bg: '#fef2f2', color: '#991b1b' },
  INVESTIGATING: { bg: '#fff7ed', color: '#9a3412' },
  RESOLVED:      { bg: '#f0fdf4', color: '#166534' },
  CLOSED:        { bg: '#f8fafc', color: '#64748b' },
};

const TYPE_ICON = {
  AVAILABILITY:   'fa-signal',
  PERFORMANCE:    'fa-gauge-high',
  ERROR:          'fa-triangle-exclamation',
  INFRASTRUCTURE: 'fa-microchip',
};

// ── Sub-components ─────────────────────────────────────────────────────────────

function SeverityBadge({ severity }) {
  const c = SEV_COLOR[severity] || SEV_COLOR.HIGH;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '.3rem',
      background: c.bg, color: c.color,
      fontSize: '.7rem', fontWeight: 700, padding: '.2em .55em',
      borderRadius: '.3rem', whiteSpace: 'nowrap',
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: c.dot, display: 'inline-block' }} />
      {severity}
    </span>
  );
}

function StatusBadge({ status }) {
  const c = STATUS_COLOR[status] || STATUS_COLOR.OPEN;
  return (
    <span style={{
      background: c.bg, color: c.color,
      fontSize: '.7rem', fontWeight: 700, padding: '.2em .55em',
      borderRadius: '.3rem', whiteSpace: 'nowrap',
    }}>
      {status}
    </span>
  );
}

function StatCard({ label, value, sub, color, icon }) {
  return (
    <div className="stat-card stat-card-col" style={{ borderTop: `3px solid ${color}` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem', marginBottom: '.25rem' }}>
        <i className={`fa-solid ${icon}`} style={{ color, fontSize: '.9rem' }} />
        <span className="stat-label" style={{ marginTop: 0 }}>{label}</span>
      </div>
      <div className="stat-value">{value ?? '—'}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────────

const EMPTY_FORM = { title: '', description: '', severity: 'HIGH', type: 'ERROR', affected_service: '', affected_route: '' };

export default function Incidents() {
  const toast   = useToast();
  const { can } = useAuth();
  const canWrite = can(PERMS.INCIDENT_WRITE);

  const [dash,     setDash]     = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [filter,   setFilter]   = useState('ALL');
  const [search,   setSearch]   = useState('');

  // Create modal
  const [modal,   setModal]   = useState(false);
  const [form,    setForm]    = useState(EMPTY_FORM);
  const [saving,  setSaving]  = useState(false);

  // Detail / edit modal
  const [detail,       setDetail]       = useState(null);
  const [detailModal,  setDetailModal]  = useState(false);
  const [editStatus,   setEditStatus]   = useState('');
  const [editSeverity, setEditSeverity] = useState('');
  const [updating,     setUpdating]     = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [d, list] = await Promise.all([
        API.getIncidentDashboard(),
        API.getIncidents(filter),
      ]);
      setDash(d);
      setIncidents(list);
    } catch { toast.error('Failed to load incidents'); }
    finally { setLoading(false); }
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  // ── Filtered list ──────────────────────────────────────────────────────────

  const filtered = incidents.filter(i =>
    !search ||
    i.title?.toLowerCase().includes(search.toLowerCase()) ||
    i.affectedService?.toLowerCase().includes(search.toLowerCase()) ||
    i.affectedRoute?.toLowerCase().includes(search.toLowerCase())
  );

  // ── Actions ────────────────────────────────────────────────────────────────

  const f = (k, v) => setForm(p => ({ ...p, [k]: v }));

  const saveIncident = async () => {
    if (!form.title) return toast.warn('Title is required.');
    setSaving(true);
    try {
      await API.createIncident(form);
      toast.success('Incident created');
      setModal(false);
      setForm(EMPTY_FORM);
      load();
    } catch (e) { toast.error('Failed: ' + (e.message || JSON.stringify(e))); }
    finally { setSaving(false); }
  };

  const openDetail = (i) => {
    setDetail(i);
    setEditStatus(i.status);
    setEditSeverity(i.severity);
    setDetailModal(true);
  };

  const resolve = async (id) => {
    try {
      await API.resolveIncident(id);
      toast.success('Incident resolved');
      setDetailModal(false);
      load();
    } catch { toast.error('Failed to resolve'); }
  };

  const updateIncident = async () => {
    if (!detail) return;
    setUpdating(true);
    try {
      await API.updateIncident(detail.id, {
        status:      editStatus,
        severity:    editSeverity,
        title:       detail.title,
        description: detail.description,
      });
      toast.success('Incident updated');
      setDetailModal(false);
      load();
    } catch { toast.error('Failed to update'); }
    finally { setUpdating(false); }
  };

  const closeIncident = async (id) => {
    if (!window.confirm('Close this incident?')) return;
    try {
      await API.closeIncident(id);
      toast.success('Incident closed');
      setDetailModal(false);
      load();
    } catch { toast.error('Failed to close'); }
  };

  const fmt = dt => dt ? new Date(dt).toLocaleString() : '—';

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* ── Dashboard stats ──────────────────────────────────────────────────── */}
      <div className="stats-grid" style={{ marginBottom: '1.25rem' }}>
        <StatCard label="Open Incidents"    value={dash?.open_incidents ?? '—'}
          color="#ef4444" icon="fa-circle-exclamation"
          sub={dash?.investigating ? `${dash.investigating} investigating` : undefined} />
        <StatCard label="Critical"          value={dash?.critical_incidents ?? '—'}
          color="#dc2626" icon="fa-bolt"
          sub="unresolved" />
        <StatCard label="Resolved (24 h)"   value={dash?.resolved_last_24h ?? '—'}
          color="#22c55e" icon="fa-circle-check" />
        <StatCard label="MTTR"              value={dash?.mttr_human ?? '—'}
          color="#6366f1" icon="fa-clock"
          sub="mean time to resolve (30 d)" />
        <StatCard label="MTBF"              value={dash?.mtbf_human ?? '—'}
          color="#3b82f6" icon="fa-calendar-xmark"
          sub="mean time between failures (30 d)" />
      </div>

      {/* ── Toolbar ──────────────────────────────────────────────────────────── */}
      <div className="filter-bar">
        <input
          className="form-control"
          style={{ width: 220 }}
          placeholder="Search title, service, route…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <select
          className="form-control"
          style={{ width: 'auto' }}
          value={filter}
          onChange={e => { setFilter(e.target.value); setSearch(''); }}
        >
          <option value="ALL">All statuses</option>
          {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>

        <div className="ms-auto" style={{ display: 'flex', gap: '.5rem' }}>
          <button className="btn btn-secondary btn-sm" onClick={load}>
            <i className="fa-solid fa-arrows-rotate" />
          </button>
          {canWrite && (
            <button className="btn btn-primary btn-sm" onClick={() => { setForm(EMPTY_FORM); setModal(true); }}>
              <i className="fa-solid fa-plus" /> New Incident
            </button>
          )}
        </div>
      </div>

      {/* ── Incident table ───────────────────────────────────────────────────── */}
      <div className="card">
        <div className="table-wrap">
          {loading
            ? <div className="loading-center"><div className="spinner" /></div>
            : (
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 32 }}></th>
                    <th>Title</th>
                    <th>Severity</th>
                    <th>Status</th>
                    <th>Service</th>
                    <th>Source</th>
                    <th>Opened</th>
                    <th>Resolved</th>
                    {canWrite && <th style={{ width: 80 }}>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {filtered.length === 0
                    ? <tr><td colSpan={canWrite ? 9 : 8}>
                        <div className="empty-state">
                          <i className="fa-solid fa-shield-check" style={{ color: '#22c55e' }} />
                          No incidents found
                        </div>
                      </td></tr>
                    : filtered.map(i => (
                      <tr key={i.id} style={{ cursor: 'pointer' }} onClick={() => openDetail(i)}>
                        <td>
                          <i className={`fa-solid ${TYPE_ICON[i.type] || 'fa-circle-dot'}`}
                            style={{ color: SEV_COLOR[i.severity]?.dot || '#94a3b8', fontSize: '.85rem' }} />
                        </td>
                        <td>
                          <div style={{ fontWeight: 600, fontSize: '.875rem' }}>{i.title}</div>
                          {i.affectedRoute && (
                            <div className="font-mono" style={{ fontSize: '.72rem', color: 'var(--muted)' }}>
                              {i.affectedRoute}
                            </div>
                          )}
                        </td>
                        <td><SeverityBadge severity={i.severity} /></td>
                        <td><StatusBadge status={i.status} /></td>
                        <td className="text-sm text-muted">{i.affectedService || '—'}</td>
                        <td>
                          <span style={{
                            fontSize: '.7rem', fontWeight: 600,
                            color: i.source === 'AUTO' ? '#6366f1' : '#64748b',
                          }}>
                            {i.source === 'AUTO'
                              ? <><i className="fa-solid fa-robot" style={{ marginRight: '.25rem' }} />AUTO</>
                              : <><i className="fa-solid fa-user" style={{ marginRight: '.25rem' }} />MANUAL</>}
                          </span>
                        </td>
                        <td className="text-sm text-muted">{fmt(i.openedAt)}</td>
                        <td className="text-sm text-muted">{fmt(i.resolvedAt)}</td>
                        {canWrite && (
                          <td onClick={e => e.stopPropagation()}>
                            <div className="actions-row">
                              {i.status !== 'RESOLVED' && i.status !== 'CLOSED' && (
                                <button
                                  className="btn-action"
                                  title="Resolve"
                                  onClick={() => resolve(i.id)}
                                  style={{ color: '#22c55e' }}
                                >
                                  <i className="fa-solid fa-circle-check" />
                                </button>
                              )}
                              <button
                                className="btn-action danger"
                                title="Close"
                                onClick={() => closeIncident(i.id)}
                              >
                                <i className="fa-solid fa-xmark" />
                              </button>
                            </div>
                          </td>
                        )}
                      </tr>
                    ))
                  }
                </tbody>
              </table>
            )
          }
        </div>
      </div>

      {/* ── Create modal ─────────────────────────────────────────────────────── */}
      <Modal
        show={modal}
        onClose={() => setModal(false)}
        title="New Incident"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setModal(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={saveIncident} disabled={saving}>
              {saving ? 'Creating…' : 'Create Incident'}
            </button>
          </>
        }
      >
        <div className="form-group">
          <label className="form-label">Title <span className="text-required">*</span></label>
          <input className="form-control" value={form.title} onChange={e => f('title', e.target.value)}
            placeholder="Brief description of the issue" />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Severity</label>
            <select className="form-control" value={form.severity} onChange={e => f('severity', e.target.value)}>
              {SEVERITIES.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Type</label>
            <select className="form-control" value={form.type} onChange={e => f('type', e.target.value)}>
              {TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Affected Service</label>
            <input className="form-control" value={form.affected_service}
              onChange={e => f('affected_service', e.target.value)} placeholder="e.g. AUTH" />
          </div>
          <div className="form-group">
            <label className="form-label">Affected Route</label>
            <input className="form-control mono" value={form.affected_route}
              onChange={e => f('affected_route', e.target.value)} placeholder="/api/..." />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">Description</label>
          <textarea className="form-control" rows={3} value={form.description}
            onChange={e => f('description', e.target.value)}
            placeholder="What is happening? What is the impact?" />
        </div>
      </Modal>

      {/* ── Detail / edit modal ───────────────────────────────────────────────── */}
      {detail && (
        <Modal
          show={detailModal}
          onClose={() => setDetailModal(false)}
          title={
            <span style={{ display: 'flex', alignItems: 'center', gap: '.5rem' }}>
              <i className={`fa-solid ${TYPE_ICON[detail.type] || 'fa-circle-dot'}`}
                style={{ color: SEV_COLOR[detail.severity]?.dot }} />
              {detail.title}
            </span>
          }
          footer={
            <>
              <button className="btn btn-secondary" onClick={() => setDetailModal(false)}>Close</button>
              {canWrite && detail.status !== 'RESOLVED' && detail.status !== 'CLOSED' && (
                <>
                  <button className="btn btn-secondary" onClick={updateIncident} disabled={updating}>
                    {updating ? 'Saving…' : 'Save Changes'}
                  </button>
                  <button className="btn btn-primary" onClick={() => resolve(detail.id)}
                    style={{ background: '#16a34a', borderColor: '#16a34a' }}>
                    <i className="fa-solid fa-circle-check" /> Resolve
                  </button>
                </>
              )}
            </>
          }
        >
          {/* Status banner */}
          <div style={{
            display: 'flex', gap: '.75rem', flexWrap: 'wrap',
            background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
            borderRadius: '.5rem', padding: '.85rem 1rem', marginBottom: '1rem',
          }}>
            <div><span style={{ fontSize: '.7rem', color: 'var(--muted)', display: 'block' }}>SEVERITY</span>
              <SeverityBadge severity={detail.severity} /></div>
            <div><span style={{ fontSize: '.7rem', color: 'var(--muted)', display: 'block' }}>STATUS</span>
              <StatusBadge status={detail.status} /></div>
            <div><span style={{ fontSize: '.7rem', color: 'var(--muted)', display: 'block' }}>TYPE</span>
              <span style={{ fontSize: '.8rem', fontWeight: 600 }}>{detail.type}</span></div>
            <div><span style={{ fontSize: '.7rem', color: 'var(--muted)', display: 'block' }}>SOURCE</span>
              <span style={{ fontSize: '.8rem', fontWeight: 600 }}>{detail.source}</span></div>
            {detail.affectedService && (
              <div><span style={{ fontSize: '.7rem', color: 'var(--muted)', display: 'block' }}>SERVICE</span>
                <span style={{ fontSize: '.8rem', fontWeight: 600 }}>{detail.affectedService}</span></div>
            )}
          </div>

          {detail.description && (
            <div style={{ fontSize: '.875rem', color: 'var(--text-secondary)', marginBottom: '1rem',
              padding: '.75rem', background: 'var(--bg-surface-alt)', borderRadius: '.4rem' }}>
              {detail.description}
            </div>
          )}

          {(detail.triggerValue || detail.triggerThreshold) && (
            <div style={{ display: 'flex', gap: '1.5rem', marginBottom: '1rem', fontSize: '.82rem' }}>
              {detail.triggerValue && (
                <div><span style={{ color: 'var(--muted)' }}>Measured: </span>
                  <strong style={{ color: '#ef4444' }}>{detail.triggerValue}</strong></div>
              )}
              {detail.triggerThreshold && (
                <div><span style={{ color: 'var(--muted)' }}>Threshold: </span>
                  <strong>{detail.triggerThreshold}</strong></div>
              )}
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem',
            fontSize: '.8rem', color: 'var(--muted)' }}>
            <div><i className="fa-regular fa-clock" style={{ marginRight: '.35rem' }} />
              Opened: <strong style={{ color: 'var(--text-primary)' }}>{fmt(detail.openedAt)}</strong></div>
            <div><i className="fa-solid fa-circle-check" style={{ marginRight: '.35rem', color: '#22c55e' }} />
              Resolved: <strong style={{ color: 'var(--text-primary)' }}>{fmt(detail.resolvedAt)}</strong></div>
          </div>

          {canWrite && detail.status !== 'RESOLVED' && detail.status !== 'CLOSED' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Update Status</label>
                <select className="form-control" value={editStatus} onChange={e => setEditStatus(e.target.value)}>
                  {STATUSES.filter(s => s !== 'CLOSED').map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div className="form-group" style={{ margin: 0 }}>
                <label className="form-label">Update Severity</label>
                <select className="form-control" value={editSeverity} onChange={e => setEditSeverity(e.target.value)}>
                  {SEVERITIES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}
