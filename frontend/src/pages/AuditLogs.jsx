import { useCallback, useEffect, useState } from 'react';
import { API } from '../api/gateway';
import { useToast } from '../context/ToastContext';

// ── Constants ──────────────────────────────────────────────────────────────────

const MODULES = ['ROUTE', 'GROUP', 'USER', 'ROLE', 'API_KEY', 'IP_ACL',
                 'INCIDENT', 'OAUTH2', 'AUTH', 'DASHBOARD', 'METRICS', 'HEALTH', 'REGISTRY'];
const ACTIONS  = ['CREATE', 'UPDATE', 'DELETE'];
const RESULTS  = ['SUCCESS', 'FAILURE'];
const PAGE_SIZE = 20;

// ── Badges ─────────────────────────────────────────────────────────────────────

function ResultBadge({ result }) {
  const ok = result === 'SUCCESS';
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: '.3rem',
      background: ok ? '#f0fdf4' : '#fef2f2',
      color: ok ? '#166534' : '#991b1b',
      fontSize: '.7rem', fontWeight: 700,
      padding: '.2em .55em', borderRadius: '.3rem', whiteSpace: 'nowrap',
    }}>
      <span style={{
        width: 6, height: 6, borderRadius: '50%',
        background: ok ? '#22c55e' : '#ef4444', display: 'inline-block',
      }} />
      {result}
    </span>
  );
}

function ActionBadge({ action }) {
  const COLOR = {
    CREATE: { bg: '#f0fdf4', color: '#166534' },
    UPDATE: { bg: '#fff7ed', color: '#9a3412' },
    DELETE: { bg: '#fef2f2', color: '#991b1b' },
  };
  const c = COLOR[action] || { bg: '#f8fafc', color: '#64748b' };
  return (
    <span style={{
      background: c.bg, color: c.color,
      fontSize: '.7rem', fontWeight: 700,
      padding: '.2em .55em', borderRadius: '.3rem', whiteSpace: 'nowrap',
    }}>
      {action}
    </span>
  );
}

function StatusBadge({ code }) {
  const color = code >= 500 ? '#991b1b' : code >= 400 ? '#9a3412' : '#166534';
  const bg    = code >= 500 ? '#fef2f2' : code >= 400 ? '#fff7ed' : '#f0fdf4';
  return (
    <span style={{
      background: bg, color,
      fontSize: '.7rem', fontWeight: 700, fontFamily: 'monospace',
      padding: '.2em .5em', borderRadius: '.3rem',
    }}>
      {code}
    </span>
  );
}

// ── JSON detail panel ──────────────────────────────────────────────────────────

function JsonPanel({ label, json }) {
  if (!json) return (
    <div style={{ flex: 1 }}>
      <div style={{ fontSize: '.65rem', fontWeight: 700, color: 'var(--muted)', marginBottom: '.35rem' }}>
        {label}
      </div>
      <div style={{
        background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
        borderRadius: '.4rem', padding: '.6rem', fontSize: '.72rem',
        color: 'var(--muted)', fontStyle: 'italic',
      }}>
        —
      </div>
    </div>
  );

  let formatted = json;
  try { formatted = JSON.stringify(JSON.parse(json), null, 2); } catch { /* keep raw */ }

  return (
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: '.65rem', fontWeight: 700, color: 'var(--muted)', marginBottom: '.35rem' }}>
        {label}
      </div>
      <pre style={{
        background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
        borderRadius: '.4rem', padding: '.6rem', margin: 0,
        fontSize: '.7rem', fontFamily: 'monospace', color: 'var(--text-secondary)',
        overflowX: 'auto', maxHeight: 260, overflowY: 'auto',
        whiteSpace: 'pre-wrap', wordBreak: 'break-all',
      }}>
        {formatted}
      </pre>
    </div>
  );
}

// ── Pagination ─────────────────────────────────────────────────────────────────

function Pagination({ page, total, size, onChange }) {
  const totalPages = Math.max(1, Math.ceil(total / size));
  if (totalPages <= 1) return null;

  const pages = [];
  const start = Math.max(0, page - 2);
  const end   = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: '.4rem',
      padding: '.75rem 1rem', borderTop: '1px solid var(--border)',
      justifyContent: 'center', flexWrap: 'wrap',
    }}>
      <button className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => onChange(0)}>
        <i className="fa-solid fa-angles-left" />
      </button>
      <button className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <i className="fa-solid fa-angle-left" />
      </button>
      {pages.map(p => (
        <button
          key={p}
          className={`btn btn-sm ${p === page ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => onChange(p)}
          style={{ minWidth: 32 }}
        >
          {p + 1}
        </button>
      ))}
      <button className="btn btn-secondary btn-sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        <i className="fa-solid fa-angle-right" />
      </button>
      <button className="btn btn-secondary btn-sm" disabled={page >= totalPages - 1} onClick={() => onChange(totalPages - 1)}>
        <i className="fa-solid fa-angles-right" />
      </button>
      <span style={{ fontSize: '.75rem', color: 'var(--muted)', marginLeft: '.5rem' }}>
        {total.toLocaleString()} total
      </span>
    </div>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────────

const EMPTY_FILTERS = { module: '', action: '', actor: '', result: '', from: '', to: '' };

export default function AuditLogs() {
  const toast = useToast();

  const [rows,       setRows]       = useState([]);
  const [total,      setTotal]      = useState(0);
  const [page,       setPage]       = useState(0);
  const [loading,    setLoading]    = useState(true);
  const [filters,    setFilters]    = useState(EMPTY_FILTERS);
  const [applied,    setApplied]    = useState(EMPTY_FILTERS);
  const [expandedId, setExpandedId] = useState(null);

  const load = useCallback(async (p = page, f = applied) => {
    setLoading(true);
    try {
      const params = { page: p, size: PAGE_SIZE };
      if (f.module) params.module = f.module;
      if (f.action) params.action = f.action;
      if (f.actor)  params.actor  = f.actor;
      if (f.result) params.result = f.result;
      if (f.from)   params.from   = f.from + ':00';
      if (f.to)     params.to     = f.to   + ':00';
      const res = await API.getAuditLogs(params);
      setRows(res.data  ?? []);
      setTotal(res.total ?? 0);
      setPage(p);
    } catch { toast.error('Failed to load audit logs'); }
    finally  { setLoading(false); }
  }, [page, applied]);

  useEffect(() => { load(0, EMPTY_FILTERS); }, []);

  const applyFilters = () => {
    setApplied({ ...filters });
    load(0, filters);
  };

  const resetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    load(0, EMPTY_FILTERS);
  };

  const filt = (k, v) => setFilters(p => ({ ...p, [k]: v }));

  const fmt = dt => dt ? new Date(dt).toLocaleString() : '—';

  const hasActive = Object.values(applied).some(v => v !== '');

  const toggleExpand = (id) => setExpandedId(prev => prev === id ? null : id);

  const hasDetail = (r) => r.oldValue || r.newValue;

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* ── Filter bar ───────────────────────────────────────────────────────── */}
      <div className="card" style={{ marginBottom: '1rem', padding: '.85rem 1rem' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '.6rem', alignItems: 'flex-end' }}>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '.25rem' }}>
            <label style={{ fontSize: '.7rem', color: 'var(--muted)', fontWeight: 600 }}>MODULE</label>
            <select className="form-control" style={{ width: 130 }}
              value={filters.module} onChange={e => filt('module', e.target.value)}>
              <option value="">All modules</option>
              {MODULES.map(m => <option key={m} value={m}>{m}</option>)}
            </select>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '.25rem' }}>
            <label style={{ fontSize: '.7rem', color: 'var(--muted)', fontWeight: 600 }}>ACTION</label>
            <select className="form-control" style={{ width: 120 }}
              value={filters.action} onChange={e => filt('action', e.target.value)}>
              <option value="">All actions</option>
              {ACTIONS.map(a => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '.25rem' }}>
            <label style={{ fontSize: '.7rem', color: 'var(--muted)', fontWeight: 600 }}>RESULT</label>
            <select className="form-control" style={{ width: 110 }}
              value={filters.result} onChange={e => filt('result', e.target.value)}>
              <option value="">All results</option>
              {RESULTS.map(r => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '.25rem' }}>
            <label style={{ fontSize: '.7rem', color: 'var(--muted)', fontWeight: 600 }}>ACTOR</label>
            <input className="form-control" style={{ width: 150 }}
              placeholder="Username…"
              value={filters.actor} onChange={e => filt('actor', e.target.value)}
              onKeyDown={e => e.key === 'Enter' && applyFilters()}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '.25rem' }}>
            <label style={{ fontSize: '.7rem', color: 'var(--muted)', fontWeight: 600 }}>FROM</label>
            <input className="form-control" type="datetime-local" style={{ width: 175 }}
              value={filters.from} onChange={e => filt('from', e.target.value)} />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '.25rem' }}>
            <label style={{ fontSize: '.7rem', color: 'var(--muted)', fontWeight: 600 }}>TO</label>
            <input className="form-control" type="datetime-local" style={{ width: 175 }}
              value={filters.to} onChange={e => filt('to', e.target.value)} />
          </div>

          <div style={{ display: 'flex', gap: '.4rem', alignSelf: 'flex-end' }}>
            <button className="btn btn-primary btn-sm" onClick={applyFilters}>
              <i className="fa-solid fa-filter" /> Apply
            </button>
            {hasActive && (
              <button className="btn btn-secondary btn-sm" onClick={resetFilters}>
                <i className="fa-solid fa-xmark" /> Clear
              </button>
            )}
            <button className="btn btn-secondary btn-sm" onClick={() => load(page, applied)}>
              <i className="fa-solid fa-arrows-rotate" />
            </button>
          </div>
        </div>

        {hasActive && (
          <div style={{ marginTop: '.6rem', display: 'flex', gap: '.4rem', flexWrap: 'wrap' }}>
            {Object.entries(applied).filter(([, v]) => v !== '').map(([k, v]) => (
              <span key={k} style={{
                fontSize: '.7rem', fontWeight: 600,
                background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
                borderRadius: '.3rem', padding: '.15em .5em', color: 'var(--text-secondary)',
              }}>
                {k.toUpperCase()}: {v}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* ── Table ────────────────────────────────────────────────────────────── */}
      <div className="card">
        <div className="table-wrap">
          {loading
            ? <div className="loading-center"><div className="spinner" /></div>
            : (
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 24 }} />
                    <th>Time</th>
                    <th>User ID</th>
                    <th>Actor</th>
                    <th>Module</th>
                    <th>Action</th>
                    <th>Entity</th>
                    <th>Status</th>
                    <th>Result</th>
                    <th>IP Address</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0
                    ? (
                      <tr><td colSpan={10}>
                        <div className="empty-state">
                          <i className="fa-solid fa-clipboard-list" style={{ color: '#94a3b8' }} />
                          No audit log entries found
                        </div>
                      </td></tr>
                    )
                    : rows.map(r => (
                      <>
                        <tr key={r.id}
                          style={{ cursor: hasDetail(r) ? 'pointer' : 'default' }}
                          onClick={() => hasDetail(r) && toggleExpand(r.id)}
                        >
                          <td style={{ textAlign: 'center', color: 'var(--muted)', fontSize: '.7rem' }}>
                            {hasDetail(r) && (
                              <i className={`fa-solid fa-chevron-${expandedId === r.id ? 'down' : 'right'}`} />
                            )}
                          </td>
                          <td className="text-sm text-muted" style={{ whiteSpace: 'nowrap' }}>
                            {fmt(r.createdAt)}
                          </td>
                          <td>
                            <span className="font-mono" style={{ fontSize: '.72rem', color: 'var(--muted)' }}>
                              {r.userId ?? '—'}
                            </span>
                          </td>
                          <td>
                            <span style={{ fontSize: '.8rem', fontWeight: 600 }}>{r.actor}</span>
                          </td>
                          <td>
                            <span style={{
                              fontSize: '.7rem', fontWeight: 700,
                              background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
                              borderRadius: '.3rem', padding: '.15em .5em', color: 'var(--text-secondary)',
                            }}>
                              {r.module}
                            </span>
                          </td>
                          <td><ActionBadge action={r.action} /></td>
                          <td className="text-sm text-muted">{r.entityId ?? '—'}</td>
                          <td>{r.statusCode ? <StatusBadge code={r.statusCode} /> : '—'}</td>
                          <td><ResultBadge result={r.result} /></td>
                          <td>
                            <span className="font-mono" style={{ fontSize: '.72rem', color: 'var(--muted)' }}>
                              {r.ipAddress ?? '—'}
                            </span>
                          </td>
                        </tr>

                        {expandedId === r.id && (
                          <tr key={`${r.id}-detail`}>
                            <td colSpan={10} style={{ padding: '0 1rem .75rem 2rem', background: 'var(--bg-surface-alt)' }}>
                              <div style={{ display: 'flex', gap: '1rem', paddingTop: '.6rem' }}>
                                <JsonPanel label="OLD VALUE" json={r.oldValue} />
                                <JsonPanel label="NEW VALUE" json={r.newValue} />
                              </div>
                            </td>
                          </tr>
                        )}
                      </>
                    ))
                  }
                </tbody>
              </table>
            )
          }
        </div>

        <Pagination
          page={page}
          total={total}
          size={PAGE_SIZE}
          onChange={p => load(p, applied)}
        />
      </div>
    </div>
  );
}