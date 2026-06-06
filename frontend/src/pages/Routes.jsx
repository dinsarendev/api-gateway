import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

const PAGE_SIZE = 15;

const EMPTY_FORM = {
  group_code: '', path: '', method: 'GET', description: '', application_id: '',
  is_public: 'N', is_encrypt: 'N', enable_circuit_breaker: 'Y',
  priority: 1, rate_limit: '', rate_limit_duration: '',
  auth_type: 'JWT', required_roles: '', required_permissions: '',
  api_type: 'REST',
  version: '', sla_tier: 'STANDARD', tags: '', documentation: '',
};

const API_TYPES = [
  { value: 'REST',      label: 'REST',      icon: 'fa-network-wired',   color: '#3b82f6' },
  { value: 'SOAP',      label: 'SOAP',      icon: 'fa-code',            color: '#8b5cf6' },
  { value: 'GRAPHQL',   label: 'GraphQL',   icon: 'fa-diagram-project', color: '#e11d48' },
  { value: 'STREAMING', label: 'Streaming', icon: 'fa-wave-square',     color: '#0891b2' },
  { value: 'AI',        label: 'AI',        icon: 'fa-robot',           color: '#059669' },
];

const SLA_TIERS = [
  { value: 'BASIC',    label: 'Basic',    cls: 'badge-sla-basic' },
  { value: 'STANDARD', label: 'Standard', cls: 'badge-sla-standard' },
  { value: 'PREMIUM',  label: 'Premium',  cls: 'badge-sla-premium' },
  { value: 'CRITICAL', label: 'Critical', cls: 'badge-sla-critical' },
];

const SUGGESTED_TAGS = ['PII', 'SENSITIVE', 'INTERNAL', 'PUBLIC_API', 'BETA'];

// ── helpers ────────────────────────────────────────────────────────────────
const truncUri  = uri => (!uri || uri.length <= 30) ? (uri || '') : uri.slice(0, 28) + '…';
const fmtDate   = iso => iso
  ? new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
  : '—';
const toLocalDateTime = s => s ? (s.length === 16 ? s + ':00' : s) : null;
const buildPayload = form => ({
  ...form,
  priority:            parseInt(form.priority, 10) || 1,
  rate_limit:          form.rate_limit          ? parseInt(form.rate_limit, 10)          : null,
  rate_limit_duration: form.rate_limit_duration ? parseInt(form.rate_limit_duration, 10) : null,
  description:         form.description    || null,
  application_id:      form.application_id || null,
  version:             form.version        || null,
  tags:                form.tags           || null,
  documentation:       form.documentation  || null,
});

// ── sub-components ─────────────────────────────────────────────────────────
function StatusBadge({ route }) {
  const { status, sunset_date, rejection_reason } = route;
  if (status === 'ACT')   return <span className="badge badge-act">Active</span>;
  if (status === 'INACT') return <span className="badge badge-inact">Inactive</span>;
  if (status === 'RETIRED') return (
    <span className="badge badge-retired">
      <i className="fa-solid fa-ban" style={{ fontSize: '.65rem' }} /> Retired
    </span>
  );
  if (status === 'DRAFT') return (
    <div>
      <span className="badge badge-draft">
        <i className="fa-solid fa-file-pen" style={{ fontSize: '.65rem' }} /> Draft
      </span>
      {rejection_reason && (
        <div style={{ fontSize: '.68rem', color: '#b45309', marginTop: '.25rem',
          maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
          title={rejection_reason}>
          <i className="fa-solid fa-circle-exclamation" style={{ marginRight: '.25rem' }} />
          {rejection_reason}
        </div>
      )}
    </div>
  );
  if (status === 'PENDING') return (
    <span className="badge badge-pending">
      <i className="fa-solid fa-hourglass-half" style={{ fontSize: '.65rem' }} /> Pending
    </span>
  );
  if (status === 'DEPRECATED') return (
    <div>
      <span className="badge badge-deprecated">
        <i className="fa-solid fa-clock-rotate-left" style={{ fontSize: '.65rem' }} /> Deprecated
      </span>
      {sunset_date && (
        <div className="text-muted" style={{ fontSize: '.68rem', marginTop: '.25rem' }}>
          <i className="fa-solid fa-calendar-xmark" style={{ marginRight: '.25rem', color: '#d97706' }} />
          Sunset {fmtDate(sunset_date)}
        </div>
      )}
    </div>
  );
  return <span className="badge badge-inact">{status}</span>;
}

function SlaBadge({ tier }) {
  const t = SLA_TIERS.find(s => s.value === tier) || SLA_TIERS[1];
  return <span className={`badge ${t.cls}`} style={{ fontSize: '.66rem' }}>{t.label}</span>;
}

function TagChips({ tags }) {
  if (!tags) return null;
  const list = tags.split(',').map(t => t.trim()).filter(Boolean);
  if (!list.length) return null;
  const shown = list.slice(0, 2);
  const rest  = list.length - shown.length;
  return (
    <div style={{ display: 'flex', gap: '.25rem', flexWrap: 'wrap', marginTop: '.25rem' }}>
      {shown.map(t => (
        <span key={t} style={{ fontSize: '.63rem', background: '#f1f5f9', color: '#475569',
          padding: '.1em .45em', borderRadius: '.25rem', fontWeight: 600 }}>
          {t}
        </span>
      ))}
      {rest > 0 && (
        <span style={{ fontSize: '.63rem', color: '#94a3b8' }} title={list.slice(2).join(', ')}>
          +{rest}
        </span>
      )}
    </div>
  );
}

function Pagination({ page, total, pageSize, onChange }) {
  const totalPages = Math.ceil(total / pageSize);
  if (totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, page - 2), end = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);
  return (
    <div className="pagination">
      <span className="page-info">
        {total === 0 ? '0 routes' : `${page * pageSize + 1}–${Math.min((page + 1) * pageSize, total)} of ${total}`}
      </span>
      <button className="page-btn" disabled={page === 0} onClick={() => onChange(0)}><i className="fa-solid fa-angles-left" /></button>
      <button className="page-btn" disabled={page === 0} onClick={() => onChange(page - 1)}><i className="fa-solid fa-angle-left" /></button>
      {pages.map(p => <button key={p} className={`page-btn${p === page ? ' active' : ''}`} onClick={() => onChange(p)}>{p + 1}</button>)}
      <button className="page-btn" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}><i className="fa-solid fa-angle-right" /></button>
      <button className="page-btn" disabled={page >= totalPages - 1} onClick={() => onChange(totalPages - 1)}><i className="fa-solid fa-angles-right" /></button>
    </div>
  );
}

function DetailRow({ r, grp, onEdit, onCopy, canWrite, canApprove, onSubmit, onApprove, onReject, onDeprecate, onUndeprecate, onRetire, onRemove, onToggle, onDoc }) {
  const fields = [
    r.description && { label: 'Description', value: r.description },
    { label: 'Auth Type', value: r.auth_type || 'JWT' },
    r.required_roles && { label: 'Required Roles', value: r.required_roles },
    r.required_permissions && { label: 'Required Permissions', value: r.required_permissions },
    { label: 'Circuit Breaker', value: r.enable_circuit_breaker === 'Y' ? 'Yes' : 'No' },
    { label: 'Priority', value: r.priority },
    r.application_id && { label: 'Application ID', value: r.application_id },
    r.is_encrypt === 'Y' && { label: 'Encrypted', value: 'Yes' },
    r.documentation && { label: 'Documentation', value: '(see doc viewer)', isDoc: true },
  ].filter(Boolean);

  return (
    <tr style={{ background: 'var(--bg-surface-alt, #f8fafc)' }}>
      <td colSpan={10} style={{ padding: '0 1rem .75rem 2.5rem', borderTop: 'none' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '.5rem 2rem', fontSize: '.78rem', color: 'var(--text-secondary, #475569)' }}>
          {fields.map(({ label, value, isDoc }) => (
            <span key={label}>
              <span style={{ fontWeight: 600, color: 'var(--text-muted, #94a3b8)', textTransform: 'uppercase', fontSize: '.67rem', letterSpacing: '.04em' }}>{label}: </span>
              {isDoc
                ? <button style={{ background: 'none', border: 'none', padding: 0, color: '#1d4ed8', cursor: 'pointer', fontSize: '.78rem', textDecoration: 'underline' }}
                    onClick={() => onDoc(r)}>View</button>
                : <span style={{ color: 'var(--text-primary, #1e293b)' }}>{value}</span>
              }
            </span>
          ))}
          {grp?.uri && (
            <span>
              <span style={{ fontWeight: 600, color: 'var(--text-muted, #94a3b8)', textTransform: 'uppercase', fontSize: '.67rem', letterSpacing: '.04em' }}>Target: </span>
              <span style={{ fontFamily: 'monospace', color: 'var(--text-primary, #1e293b)' }}>{grp.uri}</span>
            </span>
          )}
        </div>
      </td>
    </tr>
  );
}

// ── main ───────────────────────────────────────────────────────────────────
export default function Routes() {
  const toast     = useToast();
  const { can }   = useAuth();
  const canWrite   = can(PERMS.ROUTE_WRITE);
  const canApprove = can(PERMS.ROUTE_APPROVE);
  const [searchParams] = useSearchParams();
  const [expandedIds, setExpandedIds] = useState(new Set());
  const toggleExpand = id => setExpandedIds(prev => {
    const next = new Set(prev);
    next.has(id) ? next.delete(id) : next.add(id);
    return next;
  });

  // data
  const [routes, setRoutes] = useState([]);
  const [groups, setGroups] = useState([]);

  // filters
  const [status,        setStatus]        = useState('ACT');
  const [methodFilter,  setMethodFilter]  = useState('');
  const [groupFilter,   setGroupFilter]   = useState(() => searchParams.get('group') || '');
  const [apiTypeFilter, setApiTypeFilter] = useState('');
  const [versionFilter, setVersionFilter] = useState('');
  const [search,        setSearch]        = useState('');
  const [page,          setPage]          = useState(0);

  // create/edit modal
  const [loading, setLoading] = useState(true);
  const [modal,   setModal]   = useState(false);
  const [form,    setForm]    = useState(EMPTY_FORM);
  const [editId,  setEditId]  = useState(null);
  const [copying, setCopying] = useState(false);
  const [saving,  setSaving]  = useState(false);

  // deprecate modal
  const [depModal,  setDepModal]  = useState(false);
  const [depId,     setDepId]     = useState(null);
  const [depDate,   setDepDate]   = useState('');
  const [depSaving, setDepSaving] = useState(false);
  const minDepDate = new Date(Date.now() + 86400000).toISOString().slice(0, 16);

  // reject modal
  const [rejModal,   setRejModal]   = useState(false);
  const [rejId,      setRejId]      = useState(null);
  const [rejReason,  setRejReason]  = useState('');
  const [rejSaving,  setRejSaving]  = useState(false);

  // doc viewer modal
  const [docModal,  setDocModal]  = useState(false);
  const [docRoute,  setDocRoute]  = useState(null);

  // ── load ───────────────────────────────────────────────────────────────
  useEffect(() => {
    API.getGroups().then(setGroups).catch(() => toast.warn('Failed to load service groups'));
  }, []);

  const loadRoutes = useCallback(async () => {
    setLoading(true);
    try { setRoutes(await API.getRoutes(status)); }
    catch { toast.error('Failed to load routes'); }
    finally { setLoading(false); }
  }, [status]);

  useEffect(() => { loadRoutes(); }, [loadRoutes]);
  useEffect(() => { setPage(0); }, [methodFilter, groupFilter, apiTypeFilter, versionFilter, search, status]);

  const groupMap = useMemo(
    () => Object.fromEntries(groups.map(g => [g.code, g])),
    [groups]
  );

  const versions = useMemo(
    () => [...new Set(routes.map(r => r.version).filter(Boolean))].sort(),
    [routes]
  );

  // ── filter + paginate ──────────────────────────────────────────────────
  const filtered = useMemo(() =>
    routes.filter(r =>
      (!methodFilter   || r.method     === methodFilter) &&
      (!groupFilter    || r.group_code === groupFilter) &&
      (!apiTypeFilter  || (r.api_type || 'REST') === apiTypeFilter) &&
      (!versionFilter  || r.version    === versionFilter) &&
      (!search         || r.path?.toLowerCase().includes(search.toLowerCase()))
    ),
    [routes, methodFilter, groupFilter, apiTypeFilter, versionFilter, search]
  );
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  // ── form helpers ───────────────────────────────────────────────────────
  const formFromRoute = r => ({
    group_code:             r.group_code             || '',
    path:                   r.path                   || '',
    method:                 r.method                 || 'GET',
    description:            r.description            || '',
    application_id:         r.application_id         || '',
    is_public:              r.is_public              || 'N',
    is_encrypt:             r.is_encrypt             || 'N',
    enable_circuit_breaker: r.enable_circuit_breaker || 'Y',
    priority:               r.priority               ?? 1,
    rate_limit:             r.rate_limit             ?? '',
    rate_limit_duration:    r.rate_limit_duration    ?? '',
    auth_type:              r.auth_type              || 'JWT',
    required_roles:         r.required_roles         || '',
    required_permissions:   r.required_permissions   || '',
    api_type:               r.api_type               || 'REST',
    version:                r.version                || '',
    sla_tier:               r.sla_tier               || 'STANDARD',
    tags:                   r.tags                   || '',
    documentation:          r.documentation          || '',
  });

  const openCreate = () => { setEditId(null); setCopying(false); setForm(EMPTY_FORM); setModal(true); };
  const openEdit   = r  => { setEditId(r.id); setCopying(false); setForm(formFromRoute(r)); setModal(true); };
  const openCopy   = r  => { setEditId(null); setCopying(true);  setForm({ ...formFromRoute(r), path: r.path }); setModal(true); };
  const closeModal = () => { setModal(false); setSaving(false); };
  const f = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  // ── save ───────────────────────────────────────────────────────────────
  const save = async () => {
    if (!form.group_code)  return toast.warn('Please select a service group.');
    if (!form.path.trim()) return toast.warn('Path is required.');
    setSaving(true);
    try {
      const payload = buildPayload(form);
      if (editId) {
        await API.updateRoute(editId, payload);
        toast.success('Route updated');
      } else {
        await API.createRoute(payload);
        toast.success(copying ? 'Route duplicated — saved as Draft' : 'Route created as Draft — submit for approval to go live');
      }
      closeModal();
      loadRoutes();
    } catch(e) {
      toast.error('Save failed: ' + (e?.message || JSON.stringify(e)));
    } finally { setSaving(false); }
  };

  // ── lifecycle actions ──────────────────────────────────────────────────
  const toggle = async (id, action) => {
    if (!window.confirm(`${action === 'enable' ? 'Enable' : 'Disable'} route #${id}?`)) return;
    try {
      await (action === 'enable' ? API.enableRoute(id) : API.disableRoute(id));
      toast.success(`Route ${action}d`);
      loadRoutes();
    } catch(e) { toast.error(`Failed to ${action}: ` + (e?.message || '')); }
  };

  const doSubmit = async id => {
    if (!window.confirm(`Submit route #${id} for approval?`)) return;
    try { await API.submitRoute(id); toast.success('Submitted for approval'); loadRoutes(); }
    catch(e) { toast.error('Submit failed: ' + (e?.message || '')); }
  };

  const doApprove = async id => {
    if (!window.confirm(`Approve route #${id} and make it live?`)) return;
    try { await API.approveRoute(id); toast.success('Route approved and live'); loadRoutes(); }
    catch(e) { toast.error('Approve failed: ' + (e?.message || '')); }
  };

  const openRejectModal = id => { setRejId(id); setRejReason(''); setRejModal(true); };
  const closeRejModal   = () => { setRejModal(false); setRejSaving(false); };
  const doReject = async () => {
    if (!rejReason.trim()) return toast.warn('Rejection reason is required.');
    setRejSaving(true);
    try {
      await API.rejectRoute(rejId, { reason: rejReason });
      toast.success('Route rejected — returned to Draft');
      closeRejModal();
      loadRoutes();
    } catch(e) {
      toast.error('Reject failed: ' + (e?.message || ''));
    } finally { setRejSaving(false); }
  };

  const openDeprecateModal = r => { setDepId(r.id); setDepDate(''); setDepModal(true); };
  const closeDepModal      = () => { setDepModal(false); setDepSaving(false); };
  const doDeprecate = async () => {
    if (!depDate) return toast.warn('Please select a sunset date.');
    setDepSaving(true);
    try {
      await API.deprecateRoute(depId, { sunset_date: toLocalDateTime(depDate) });
      toast.success('Route deprecated — deprecation headers will be injected');
      closeDepModal();
      loadRoutes();
    } catch(e) {
      toast.error('Deprecate failed: ' + (e?.message || ''));
    } finally { setDepSaving(false); }
  };

  const doUndeprecate = async id => {
    if (!window.confirm(`Restore route #${id} to active?`)) return;
    try { await API.undeprecateRoute(id); toast.success('Route restored to active'); loadRoutes(); }
    catch { toast.error('Restore failed'); }
  };

  const doRetire = async id => {
    if (!window.confirm(`Retire route #${id} immediately? It will stop serving traffic.`)) return;
    try { await API.retireRoute(id); toast.success('Route retired'); loadRoutes(); }
    catch { toast.error('Retire failed'); }
  };

  const remove = async id => {
    if (!window.confirm(`Delete route #${id}?`)) return;
    try { await API.deleteRoute(id); toast.success('Route deleted'); loadRoutes(); }
    catch { toast.error('Delete failed'); }
  };

  const reload = async () => {
    try { await API.reloadRoutes(); toast.success('Gateway routes reloaded'); }
    catch { toast.error('Reload failed'); }
  };

  const openDoc = r => { setDocRoute(r); setDocModal(true); };

  const modalTitle = editId
    ? `Edit Route #${editId}`
    : copying ? 'Duplicate Route' : 'New Route';

  // ── render ─────────────────────────────────────────────────────────────
  return (
    <div>
      {/* Filter bar */}
      <div className="filter-bar">
        <select className="form-select" style={{ width: 'auto' }} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="ACT">Active</option>
          <option value="DRAFT">Draft</option>
          <option value="PENDING">Pending Approval</option>
          <option value="DEPRECATED">Deprecated</option>
          <option value="RETIRED">Retired</option>
          <option value="INACT">Inactive</option>
        </select>

        <select className="form-select" style={{ width: 'auto' }} value={methodFilter} onChange={e => setMethodFilter(e.target.value)}>
          <option value="">All Methods</option>
          {['GET','POST','PUT','DELETE','PATCH'].map(m => <option key={m}>{m}</option>)}
        </select>

        <select className="form-select" style={{ width: 'auto' }} value={apiTypeFilter} onChange={e => setApiTypeFilter(e.target.value)}>
          <option value="">All Types</option>
          {API_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select>

        <select className="form-select" style={{ width: 'auto' }} value={groupFilter} onChange={e => setGroupFilter(e.target.value)}>
          <option value="">All Groups</option>
          {groups.map(g => <option key={g.code} value={g.code}>{g.code}</option>)}
        </select>

        {versions.length > 0 && (
          <select className="form-select" style={{ width: 'auto' }} value={versionFilter} onChange={e => setVersionFilter(e.target.value)}>
            <option value="">All Versions</option>
            {versions.map(v => <option key={v} value={v}>{v}</option>)}
          </select>
        )}

        <input className="form-control" style={{ width: 200 }} placeholder="Search path…"
          value={search} onChange={e => setSearch(e.target.value)} />

        <div className="ms-auto">
          {canWrite && <button className="btn btn-secondary btn-sm" onClick={reload}><i className="fa-solid fa-arrows-rotate" /> Reload Gateway</button>}
          {canWrite && <button className="btn btn-primary btn-sm" onClick={openCreate}><i className="fa-solid fa-plus" /> New Route</button>}
        </div>
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-wrap">
          {loading
            ? <div className="loading-center"><div className="spinner" /></div>
            : (
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>ID</th>
                    <th>Path / Version</th>
                    <th>Method</th>
                    <th>Type</th>
                    <th>Group / Target</th>
                    <th>Public</th>
                    <th>Rate Limit</th>
                    <th>Status / SLA</th>
                    <th style={{ width: 170 }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {paged.length === 0
                    ? <tr><td colSpan={10}><div className="empty-state"><i className="fa-solid fa-inbox" />No routes found</div></td></tr>
                    : paged.map(r => {
                      const grp = groupMap[r.group_code];
                      const expanded = expandedIds.has(r.id);
                      return (
                        <React.Fragment key={r.id}>
                        <tr>
                          <td style={{ padding: '0 .25rem', textAlign: 'center' }}>
                            <button
                              className="btn-action"
                              style={{ width: 22, height: 22, padding: 0, border: 'none', background: 'none', color: '#94a3b8', transition: 'transform .15s' }}
                              title={expanded ? 'Collapse' : 'Expand details'}
                              onClick={() => toggleExpand(r.id)}
                            >
                              <i className={`fa-solid fa-chevron-right`} style={{ fontSize: '.65rem', transform: expanded ? 'rotate(90deg)' : 'none', display: 'inline-block', transition: 'transform .15s' }} />
                            </button>
                          </td>
                          <td className="text-muted text-sm">#{r.id}</td>
                          <td>
                            <div className="path-cell" title={r.path}>{r.path}</div>
                            <div style={{ display: 'flex', gap: '.3rem', marginTop: '.2rem', flexWrap: 'wrap', alignItems: 'center' }}>
                              {r.version && (
                                <span style={{ fontSize: '.63rem', background: '#e0f2fe', color: '#0369a1',
                                  padding: '.1em .45em', borderRadius: '.25rem', fontWeight: 700 }}>
                                  {r.version}
                                </span>
                              )}
                              <TagChips tags={r.tags} />
                            </div>
                          </td>
                          <td><span className={`method-badge method-${r.method}`}>{r.method || '—'}</span></td>
                          <td>
                            {(() => {
                              const t = API_TYPES.find(t => t.value === (r.api_type || 'REST'));
                              return t ? (
                                <span style={{ fontSize: '.72rem', fontWeight: 600, color: t.color,
                                  background: t.color + '18', padding: '2px 7px', borderRadius: 4, whiteSpace: 'nowrap' }}>
                                  <i className={`fa-solid ${t.icon}`} style={{ marginRight: '.3rem' }} />{t.label}
                                </span>
                              ) : null;
                            })()}
                          </td>
                          <td>
                            <span className="fw-bold" style={{ color: '#1d4ed8', fontSize: '.82rem' }}>{r.group_code || '—'}</span>
                            {grp?.uri && (
                              <div className="text-muted" style={{ fontSize: '.7rem', marginTop: '.1rem' }} title={grp.uri}>
                                {grp.uri.startsWith('lb://') && <span className="lb-badge" style={{ marginRight: '.25rem' }}>lb</span>}
                                {truncUri(grp.uri)}
                              </div>
                            )}
                          </td>
                          <td><span style={{ fontSize: '.75rem', fontWeight: 600 }}>{r.is_public === 'Y' ? '✓' : '—'}</span></td>
                          <td className="text-sm text-muted">{r.rate_limit ? `${r.rate_limit} / ${r.rate_limit_duration}s` : '—'}</td>
                          <td>
                            <StatusBadge route={r} />
                            <div style={{ marginTop: '.25rem' }}>
                              <SlaBadge tier={r.sla_tier || 'STANDARD'} />
                            </div>
                          </td>
                          <td>
                            <div className="actions-row">
                              {/* Edit — not for retired */}
                              {canWrite && r.status !== 'RETIRED' && (
                                <button className="btn-action" title="Edit" onClick={() => openEdit(r)}>
                                  <i className="fa-solid fa-pen" />
                                </button>
                              )}
                              {/* Duplicate always */}
                              {canWrite && (
                                <button className="btn-action" title="Duplicate" onClick={() => openCopy(r)}>
                                  <i className="fa-solid fa-copy" />
                                </button>
                              )}

                              {/* DRAFT: submit */}
                              {canWrite && r.status === 'DRAFT' && (
                                <button className="btn-action" title="Submit for Approval"
                                  onClick={() => doSubmit(r.id)}
                                  style={{ borderColor: '#3b82f6', color: '#1d4ed8' }}>
                                  <i className="fa-solid fa-paper-plane" />
                                </button>
                              )}

                              {/* PENDING: approve / reject */}
                              {r.status === 'PENDING' && canApprove && (<>
                                <button className="btn-action success" title="Approve" onClick={() => doApprove(r.id)}>
                                  <i className="fa-solid fa-check" />
                                </button>
                                <button className="btn-action danger" title="Reject" onClick={() => openRejectModal(r.id)}>
                                  <i className="fa-solid fa-xmark" />
                                </button>
                              </>)}
                              {r.status === 'PENDING' && !canApprove && (
                                <span style={{ fontSize: '.7rem', color: '#7c3aed', padding: '0 .3rem' }}>
                                  <i className="fa-solid fa-hourglass-half" style={{ marginRight: '.2rem' }} />Awaiting
                                </span>
                              )}

                              {/* ACT: deprecate + disable */}
                              {canWrite && r.status === 'ACT' && (<>
                                <button className="btn-action warning" title="Deprecate" onClick={() => openDeprecateModal(r)}>
                                  <i className="fa-solid fa-clock-rotate-left" />
                                </button>
                                <button className="btn-action warning" title="Disable" onClick={() => toggle(r.id, 'disable')}>
                                  <i className="fa-solid fa-pause" />
                                </button>
                              </>)}

                              {/* DEPRECATED: restore or retire */}
                              {canWrite && r.status === 'DEPRECATED' && (<>
                                <button className="btn-action success" title="Restore to Active" onClick={() => doUndeprecate(r.id)}>
                                  <i className="fa-solid fa-rotate-left" />
                                </button>
                                <button className="btn-action danger" title="Retire Now" onClick={() => doRetire(r.id)}>
                                  <i className="fa-solid fa-ban" />
                                </button>
                              </>)}

                              {/* INACT: re-enable */}
                              {canWrite && r.status === 'INACT' && (
                                <button className="btn-action success" title="Enable" onClick={() => toggle(r.id, 'enable')}>
                                  <i className="fa-solid fa-play" />
                                </button>
                              )}

                              {/* Doc viewer — when documentation exists */}
                              {r.documentation && (
                                <button className="btn-action" title="View Documentation" onClick={() => openDoc(r)}>
                                  <i className="fa-solid fa-book-open" />
                                </button>
                              )}

                              {/* Delete — not for deprecated (must retire first) */}
                              {canWrite && r.status !== 'DEPRECATED' && (
                                <button className="btn-action danger" title="Delete" onClick={() => remove(r.id)}>
                                  <i className="fa-solid fa-trash" />
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                        {expanded && (
                          <DetailRow r={r} grp={grp} canWrite={canWrite} canApprove={canApprove} onDoc={openDoc} />
                        )}
                        </React.Fragment>
                      );
                    })}
                </tbody>
              </table>
            )}
        </div>
        <Pagination page={page} total={filtered.length} pageSize={PAGE_SIZE} onChange={setPage} />
      </div>

      {/* ── Create / Edit / Copy Modal ───────────────────────────────────── */}
      <Modal show={modal} onClose={closeModal} title={modalTitle} size="lg"
        footer={<>
          <button className="btn btn-secondary" onClick={closeModal}>Cancel</button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>
            {saving ? 'Saving…' : editId ? 'Update Route' : copying ? 'Duplicate Route' : 'Create Route'}
          </button>
        </>}
      >
        {/* Draft info banner */}
        {!editId && !copying && (
          <div style={{ display: 'flex', gap: '.75rem', background: '#dbeafe', border: '1px solid #bfdbfe',
            borderRadius: '.5rem', padding: '.85rem 1rem', marginBottom: '1.25rem', alignItems: 'flex-start' }}>
            <i className="fa-solid fa-circle-info" style={{ color: '#1d4ed8', marginTop: '.1rem', flexShrink: 0 }} />
            <div style={{ fontSize: '.85rem', color: '#1e40af', lineHeight: 1.5 }}>
              New routes start as <strong>Draft</strong>. After saving, submit for approval to make the route live.
            </div>
          </div>
        )}

        {/* Route Settings */}
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Group Code <span className="text-required">*</span></label>
            <select className="form-select" value={form.group_code} onChange={e => f('group_code', e.target.value)}>
              <option value="">— select group —</option>
              {groups.map(g => <option key={g.code} value={g.code}>{g.code} — {truncUri(g.uri)}</option>)}
              {form.group_code && !groupMap[form.group_code] && (
                <option value={form.group_code}>{form.group_code} (not in active groups)</option>
              )}
            </select>
            {form.group_code && groupMap[form.group_code] && (
              <div className="form-hint"><i className="fa-solid fa-arrow-right" style={{ marginRight: '.3rem' }} />{groupMap[form.group_code].uri}</div>
            )}
          </div>
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Path <span className="text-required">*</span></label>
            <input className="form-control mono" value={form.path} onChange={e => f('path', e.target.value)} placeholder="/api/v1/resource/{id}" />
          </div>
          <div className="form-group">
            <label className="form-label">API Type</label>
            <select className="form-select" value={form.api_type} onChange={e => f('api_type', e.target.value)}>
              {API_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
            {form.api_type === 'GRAPHQL'  && <div className="form-hint"><i className="fa-solid fa-circle-info" style={{ marginRight: '.3rem' }} />GET &amp; POST auto-allowed</div>}
            {(form.api_type === 'STREAMING' || form.api_type === 'AI') && <div className="form-hint"><i className="fa-solid fa-circle-info" style={{ marginRight: '.3rem' }} />SSE — 30 min timeout</div>}
            {form.api_type === 'SOAP'     && <div className="form-hint"><i className="fa-solid fa-circle-info" style={{ marginRight: '.3rem' }} />Injects Content-Type: text/xml</div>}
          </div>
          <div className="form-group">
            <label className="form-label">Method</label>
            <select className="form-select" value={form.method} onChange={e => f('method', e.target.value)} disabled={form.api_type === 'GRAPHQL'}>
              {['GET','POST','PUT','DELETE','PATCH'].map(m => <option key={m}>{m}</option>)}
            </select>
            {form.api_type === 'GRAPHQL' && <div className="form-hint">Not required for GraphQL</div>}
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">Description</label>
          <input className="form-control" value={form.description} onChange={e => f('description', e.target.value)} placeholder="Brief description of this route…" />
        </div>

        <div className="form-row">
          {[
            ['is_public',             'Is Public',       ['N','Y'], { N: 'No (auth required)', Y: 'Yes (open)' }],
            ['is_encrypt',            'Encrypt',         ['N','Y'], { N: 'No', Y: 'Yes' }],
            ['enable_circuit_breaker','Circuit Breaker', ['Y','N'], { Y: 'Yes', N: 'No' }],
          ].map(([key, label, opts, labels]) => (
            <div key={key} className="form-group">
              <label className="form-label">{label}</label>
              <select className="form-select" value={form[key]} onChange={e => f(key, e.target.value)}>
                {opts.map(o => <option key={o} value={o}>{labels[o]}</option>)}
              </select>
            </div>
          ))}
          <div className="form-group">
            <label className="form-label">Priority</label>
            <input className="form-control" type="number" min="1" value={form.priority} onChange={e => f('priority', e.target.value)} />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Rate Limit (req)</label>
            <input className="form-control" type="number" min="1" value={form.rate_limit} onChange={e => f('rate_limit', e.target.value)} placeholder="e.g. 100" />
          </div>
          <div className="form-group">
            <label className="form-label">Window (sec)</label>
            <input className="form-control" type="number" min="1" value={form.rate_limit_duration} onChange={e => f('rate_limit_duration', e.target.value)} placeholder="e.g. 60" />
          </div>
          <div className="form-group">
            <label className="form-label">Application ID</label>
            <input className="form-control" value={form.application_id} onChange={e => f('application_id', e.target.value)} placeholder="optional" />
          </div>
        </div>

        {/* Security */}
        <div style={{ borderTop: '1px solid #e2e8f0', marginTop: '.5rem', paddingTop: '1rem' }}>
          <div style={{ fontSize: '.78rem', fontWeight: 700, color: '#64748b', letterSpacing: '.06em', textTransform: 'uppercase', marginBottom: '.75rem' }}>
            <i className="fa-solid fa-shield-halved" style={{ marginRight: '.4rem' }} />Security
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Auth Type</label>
              <select className="form-select" value={form.auth_type} onChange={e => f('auth_type', e.target.value)}>
                <option value="JWT">JWT</option>
                <option value="OAUTH2">OAuth2 (Introspection)</option>
                <option value="API_KEY">API Key</option>
                <option value="NONE">None</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Required Roles</label>
              <input className="form-control" value={form.required_roles} onChange={e => f('required_roles', e.target.value)}
                placeholder="ADMIN,MANAGER" disabled={form.is_public === 'Y' || form.auth_type === 'NONE'} />
              <div className="form-hint">All listed roles must be present.</div>
            </div>
            <div className="form-group">
              <label className="form-label">Required Permissions</label>
              <input className="form-control" value={form.required_permissions} onChange={e => f('required_permissions', e.target.value)}
                placeholder="read:users,write:orders" disabled={form.is_public === 'Y' || form.auth_type === 'NONE'} />
              <div className="form-hint">All listed permissions must be present.</div>
            </div>
          </div>
        </div>

        {/* Governance */}
        <div style={{ borderTop: '1px solid #e2e8f0', marginTop: '.5rem', paddingTop: '1rem' }}>
          <div style={{ fontSize: '.78rem', fontWeight: 700, color: '#64748b', letterSpacing: '.06em', textTransform: 'uppercase', marginBottom: '.75rem' }}>
            <i className="fa-solid fa-scale-balanced" style={{ marginRight: '.4rem' }} />Governance
          </div>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Version</label>
              <input className="form-control" value={form.version} onChange={e => f('version', e.target.value)}
                placeholder="v1" />
              <div className="form-hint">Injects <code>X-API-Version</code> response header.</div>
            </div>
            <div className="form-group">
              <label className="form-label">SLA Tier</label>
              <select className="form-select" value={form.sla_tier} onChange={e => f('sla_tier', e.target.value)}>
                {SLA_TIERS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
              </select>
              <div className="form-hint">Expected service availability tier.</div>
            </div>
            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label className="form-label">Policy Tags</label>
              <input className="form-control" value={form.tags} onChange={e => f('tags', e.target.value)}
                placeholder="PII, SENSITIVE, INTERNAL, BETA, PUBLIC_API" />
              <div className="form-hint">
                Suggested: {SUGGESTED_TAGS.map(t => (
                  <button key={t} onClick={() => {
                    const cur = form.tags.split(',').map(x => x.trim()).filter(Boolean);
                    if (!cur.includes(t)) f('tags', [...cur, t].join(', '));
                  }} style={{ background: 'none', border: '1px solid #cbd5e1', borderRadius: '.2rem',
                    padding: '.1em .4em', fontSize: '.7rem', cursor: 'pointer', marginRight: '.25rem',
                    color: '#475569' }}>
                    {t}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Documentation */}
        <div style={{ borderTop: '1px solid #e2e8f0', marginTop: '.5rem', paddingTop: '1rem' }}>
          <div style={{ fontSize: '.78rem', fontWeight: 700, color: '#64748b', letterSpacing: '.06em', textTransform: 'uppercase', marginBottom: '.75rem' }}>
            <i className="fa-solid fa-book-open" style={{ marginRight: '.4rem' }} />Documentation
          </div>
          <div className="form-group">
            <label className="form-label">API Notes (Markdown)</label>
            <textarea className="form-control" rows={5} value={form.documentation}
              onChange={e => f('documentation', e.target.value)}
              placeholder={`## Overview\nDescribe what this API does.\n\n## Request\n...\n\n## Response\n...`}
              style={{ fontFamily: 'monospace', fontSize: '.82rem', resize: 'vertical' }} />
            <div className="form-hint">Visible to reviewers and in the route documentation viewer.</div>
          </div>
        </div>
      </Modal>

      {/* ── Deprecate Modal ──────────────────────────────────────────────── */}
      <Modal show={depModal} onClose={closeDepModal} title={`Deprecate Route #${depId}`}
        footer={<>
          <button className="btn btn-secondary" onClick={closeDepModal}>Cancel</button>
          <button className="btn" onClick={doDeprecate} disabled={depSaving}
            style={{ background: '#d97706', color: '#fff', borderColor: '#d97706' }}>
            {depSaving ? 'Deprecating…' : 'Deprecate Route'}
          </button>
        </>}
      >
        <div style={{ display: 'flex', gap: '.75rem', background: '#fef3c7', border: '1px solid #fde68a',
          borderRadius: '.5rem', padding: '.85rem 1rem', marginBottom: '1.25rem', alignItems: 'flex-start' }}>
          <i className="fa-solid fa-triangle-exclamation" style={{ color: '#d97706', marginTop: '.1rem', flexShrink: 0 }} />
          <div style={{ fontSize: '.85rem', color: '#92400e', lineHeight: 1.5 }}>
            The route will keep serving traffic but responses will include
            <code style={{ margin: '0 .3rem', background: '#fde68a', padding: '.1em .35em', borderRadius: '.2rem' }}>Deprecation: true</code>
            and <code style={{ margin: '0 .3rem', background: '#fde68a', padding: '.1em .35em', borderRadius: '.2rem' }}>Sunset:</code> headers.
            It will be automatically retired after the sunset date.
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">Sunset Date <span className="text-required">*</span></label>
          <input type="datetime-local" className="form-control" value={depDate} min={minDepDate} onChange={e => setDepDate(e.target.value)} />
          <div className="form-hint">The gateway auto-retires the route after this date. Must be in the future.</div>
        </div>
      </Modal>

      {/* ── Reject Modal ─────────────────────────────────────────────────── */}
      <Modal show={rejModal} onClose={closeRejModal} title={`Reject Route #${rejId}`}
        footer={<>
          <button className="btn btn-secondary" onClick={closeRejModal}>Cancel</button>
          <button className="btn btn-danger" onClick={doReject} disabled={rejSaving}>
            {rejSaving ? 'Rejecting…' : 'Reject & Return to Draft'}
          </button>
        </>}
      >
        <div style={{ display: 'flex', gap: '.75rem', background: '#fee2e2', border: '1px solid #fca5a5',
          borderRadius: '.5rem', padding: '.85rem 1rem', marginBottom: '1.25rem', alignItems: 'flex-start' }}>
          <i className="fa-solid fa-triangle-exclamation" style={{ color: '#dc2626', marginTop: '.1rem', flexShrink: 0 }} />
          <div style={{ fontSize: '.85rem', color: '#991b1b', lineHeight: 1.5 }}>
            The route will be returned to <strong>Draft</strong> status. The rejection reason will be visible to the route author.
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">Rejection Reason <span className="text-required">*</span></label>
          <textarea className="form-control" rows={4} value={rejReason} onChange={e => setRejReason(e.target.value)}
            placeholder="Explain what needs to be changed before re-submission…" />
        </div>
      </Modal>

      {/* ── Documentation Viewer Modal ───────────────────────────────────── */}
      <Modal show={docModal} onClose={() => setDocModal(false)}
        title={docRoute ? `Documentation — ${docRoute.method} ${docRoute.path}` : ''}
        size="lg"
        footer={<button className="btn btn-secondary" onClick={() => setDocModal(false)}>Close</button>}
      >
        {docRoute && (
          <div>
            <div style={{ display: 'flex', gap: '.5rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
              {docRoute.version && (
                <span style={{ background: '#e0f2fe', color: '#0369a1', padding: '.2em .55em',
                  borderRadius: '.3rem', fontSize: '.75rem', fontWeight: 700 }}>{docRoute.version}</span>
              )}
              <SlaBadge tier={docRoute.sla_tier || 'STANDARD'} />
              <StatusBadge route={docRoute} />
            </div>
            {docRoute.tags && (
              <div style={{ marginBottom: '1rem' }}>
                <div style={{ fontSize: '.75rem', fontWeight: 600, color: '#64748b', marginBottom: '.4rem' }}>POLICY TAGS</div>
                <div style={{ display: 'flex', gap: '.35rem', flexWrap: 'wrap' }}>
                  {docRoute.tags.split(',').map(t => t.trim()).filter(Boolean).map(t => (
                    <span key={t} style={{ background: '#f1f5f9', color: '#334155', padding: '.2em .55em',
                      borderRadius: '.3rem', fontSize: '.75rem', fontWeight: 600 }}>{t}</span>
                  ))}
                </div>
              </div>
            )}
            <div style={{ fontSize: '.75rem', fontWeight: 600, color: '#64748b', marginBottom: '.5rem' }}>DOCUMENTATION</div>
            <pre style={{ background: 'var(--bg-surface-alt)', border: '1px solid var(--border)',
              borderRadius: '.5rem', padding: '1rem', fontFamily: 'monospace', fontSize: '.82rem',
              whiteSpace: 'pre-wrap', wordBreak: 'break-word', color: 'var(--text-primary)',
              maxHeight: 400, overflowY: 'auto', margin: 0 }}>
              {docRoute.documentation}
            </pre>
          </div>
        )}
      </Modal>
    </div>
  );
}
