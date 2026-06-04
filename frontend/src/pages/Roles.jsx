import { useCallback, useEffect, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

const PAGE_SIZE = 15;

const EMPTY_FORM = { name: '', description: '', permissionIds: [] };

// ── Permission group labels ────────────────────────────────────────────────
const PERM_GROUPS = {
  ROUTE:    ['ROUTE_READ',    'ROUTE_WRITE'],
  GROUP:    ['GROUP_READ',    'GROUP_WRITE'],
  REGISTRY: ['REGISTRY_READ', 'REGISTRY_WRITE'],
  HEALTH:   ['HEALTH_READ'],
  SECURITY: ['SECURITY_READ', 'SECURITY_WRITE'],
  USER:     ['USER_READ',     'USER_WRITE'],
  ROLE:     ['ROLE_READ',     'ROLE_WRITE'],
};

// ── Pagination ─────────────────────────────────────────────────────────────
function Pagination({ page, total, pageSize, onChange }) {
  const totalPages = Math.ceil(total / pageSize);
  if (totalPages <= 1) return null;

  const pages = [];
  const start = Math.max(0, page - 2);
  const end   = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);

  return (
    <div className="pagination">
      <span className="page-info">
        {total === 0
          ? '0 roles'
          : `${page * pageSize + 1}–${Math.min((page + 1) * pageSize, total)} of ${total}`}
      </span>
      <button className="page-btn" disabled={page === 0} onClick={() => onChange(0)}>
        <i className="fa-solid fa-angles-left" />
      </button>
      <button className="page-btn" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <i className="fa-solid fa-angle-left" />
      </button>
      {pages.map(p => (
        <button key={p} className={`page-btn${p === page ? ' active' : ''}`} onClick={() => onChange(p)}>
          {p + 1}
        </button>
      ))}
      <button className="page-btn" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        <i className="fa-solid fa-angle-right" />
      </button>
      <button className="page-btn" disabled={page >= totalPages - 1} onClick={() => onChange(totalPages - 1)}>
        <i className="fa-solid fa-angles-right" />
      </button>
    </div>
  );
}

// ── Permission checkbox grid ───────────────────────────────────────────────
function PermissionGrid({ allPermissions, selected, onChange }) {
  const permMap = Object.fromEntries(allPermissions.map(p => [p.name, p]));
  const selectedIds = new Set(selected);

  const toggle = (id) => {
    onChange(selectedIds.has(id)
      ? selected.filter(x => x !== id)
      : [...selected, id]);
  };

  const toggleGroup = (names) => {
    const groupIds = names.map(n => permMap[n]?.id).filter(Boolean);
    const allSelected = groupIds.every(id => selectedIds.has(id));
    if (allSelected) {
      onChange(selected.filter(id => !groupIds.includes(id)));
    } else {
      const next = new Set(selected);
      groupIds.forEach(id => next.add(id));
      onChange([...next]);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '.75rem' }}>
      {Object.entries(PERM_GROUPS).map(([group, names]) => {
        const perms    = names.map(n => permMap[n]).filter(Boolean);
        const allSel   = perms.length > 0 && perms.every(p => selectedIds.has(p.id));
        const someSel  = perms.some(p => selectedIds.has(p.id));

        return (
          <div key={group} style={{
            border: '1px solid #e2e8f0', borderRadius: 6, overflow: 'hidden',
          }}>
            {/* Group header */}
            <div
              style={{
                display: 'flex', alignItems: 'center', gap: '.5rem',
                padding: '.4rem .75rem',
                background: allSel ? '#eff6ff' : someSel ? '#fefce8' : '#f8fafc',
                borderBottom: '1px solid #e2e8f0', cursor: 'pointer',
                fontSize: '.78rem', fontWeight: 700, color: '#475569',
                letterSpacing: '.04em', textTransform: 'uppercase',
              }}
              onClick={() => toggleGroup(names)}
            >
              <input
                type="checkbox"
                readOnly
                checked={allSel}
                ref={el => { if (el) el.indeterminate = someSel && !allSel; }}
                style={{ cursor: 'pointer' }}
                onClick={e => { e.stopPropagation(); toggleGroup(names); }}
              />
              {group}
              <span style={{ marginLeft: 'auto', fontWeight: 400, fontSize: '.72rem', color: '#94a3b8' }}>
                {perms.filter(p => selectedIds.has(p.id)).length}/{perms.length} selected
              </span>
            </div>

            {/* Permissions */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 0 }}>
              {perms.map((p, i) => (
                <label
                  key={p.id}
                  style={{
                    display: 'flex', alignItems: 'flex-start', gap: '.4rem',
                    padding: '.4rem .75rem', cursor: 'pointer', flex: '1 1 50%',
                    borderRight: i % 2 === 0 ? '1px solid #f1f5f9' : 'none',
                    borderTop: i >= 2 ? '1px solid #f1f5f9' : 'none',
                    background: selectedIds.has(p.id) ? '#f0f9ff' : 'transparent',
                    transition: 'background .1s',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={selectedIds.has(p.id)}
                    onChange={() => toggle(p.id)}
                    style={{ marginTop: '.1rem', cursor: 'pointer', flexShrink: 0 }}
                  />
                  <div>
                    <div style={{ fontSize: '.8rem', fontWeight: 600, color: '#1e293b' }}>{p.name}</div>
                    {p.description && (
                      <div style={{ fontSize: '.7rem', color: '#64748b', marginTop: '.05rem' }}>
                        {p.description}
                      </div>
                    )}
                  </div>
                </label>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ── Main component ─────────────────────────────────────────────────────────
export default function Roles() {
  const toast    = useToast();
  const { can }  = useAuth();
  const canWrite = can(PERMS.ROLE_WRITE);

  const [roles,       setRoles]       = useState([]);
  const [total,       setTotal]       = useState(0);
  const [allPerms,    setAllPerms]    = useState([]);

  const [status,  setStatus]  = useState('ACT');
  const [search,  setSearch]  = useState('');
  const [page,    setPage]    = useState(0);

  const [loading, setLoading] = useState(true);
  const [modal,   setModal]   = useState(false);
  const [form,    setForm]    = useState(EMPTY_FORM);
  const [editId,  setEditId]  = useState(null);
  const [saving,  setSaving]  = useState(false);

  // ── Load ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    API.getPermissions()
      .then(data => setAllPerms(Array.isArray(data) ? data : []))
      .catch(() => toast.warn('Failed to load permissions'));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await API.getRoles({ status, search, page, size: PAGE_SIZE });
      setRoles(res.data);
      setTotal(res.total);
    } catch {
      toast.error('Failed to load roles');
    } finally {
      setLoading(false);
    }
  }, [status, search, page]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { setPage(0); }, [status, search]);

  // ── Modal helpers ─────────────────────────────────────────────────────────
  const openCreate = () => {
    setEditId(null);
    setForm(EMPTY_FORM);
    setModal(true);
  };

  const openEdit = r => {
    setEditId(r.id);
    setForm({
      name:          r.name        || '',
      description:   r.description || '',
      permissionIds: (r.permissions || []).map(p => p.id),
    });
    setModal(true);
  };

  const closeModal = () => { setModal(false); setSaving(false); };

  const f = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  // ── Save ──────────────────────────────────────────────────────────────────
  const save = async () => {
    if (!form.name.trim()) return toast.warn('Role name is required.');

    const payload = {
      name:           form.name.toUpperCase().replace(/\s+/g, '_'),
      description:    form.description || null,
      permission_ids: form.permissionIds,
    };

    setSaving(true);
    try {
      if (editId) {
        await API.updateRole(editId, payload);
        toast.success('Role updated');
      } else {
        await API.createRole(payload);
        toast.success('Role created');
      }
      closeModal();
      load();
    } catch (e) {
      const msg = e?.message || (typeof e === 'object' ? JSON.stringify(e) : String(e));
      toast.error('Save failed: ' + msg);
    } finally {
      setSaving(false);
    }
  };

  // ── Status toggle ─────────────────────────────────────────────────────────
  const toggleStatus = async (r) => {
    const next  = r.status === 'ACT' ? 'INACT' : 'ACT';
    const label = next === 'ACT' ? 'activate' : 'deactivate';
    if (!window.confirm(`${label.charAt(0).toUpperCase() + label.slice(1)} role "${r.name}"?`)) return;
    try {
      await API.updateRoleStatus(r.id, next);
      toast.success(`Role ${label}d`);
      load();
    } catch { toast.error(`Failed to ${label} role`); }
  };

  const fmtDate = dt => dt ? new Date(dt).toLocaleDateString() : '—';

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div>
      {/* Filter bar */}
      <div className="filter-bar">
        <select className="form-select" style={{ width: 'auto' }} value={status}
          onChange={e => setStatus(e.target.value)}>
          <option value="ACT">Active</option>
          <option value="INACT">Inactive</option>
          <option value="">All</option>
        </select>

        <input
          className="form-control"
          style={{ width: 220 }}
          placeholder="Search role name or description…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />

        {canWrite && (
          <div className="ms-auto">
            <button className="btn btn-primary btn-sm" onClick={openCreate}>
              <i className="fa-solid fa-plus" /> New Role
            </button>
          </div>
        )}
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
                    <th>ID</th>
                    <th>Name</th>
                    <th>Description</th>
                    <th>Permissions</th>
                    <th>Created</th>
                    <th>Status</th>
                    <th style={{ width: 96 }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {roles.length === 0
                    ? (
                      <tr>
                        <td colSpan={7}>
                          <div className="empty-state">
                            <i className="fa-solid fa-user-shield" />No roles found
                          </div>
                        </td>
                      </tr>
                    )
                    : roles.map(r => (
                      <tr key={r.id}>
                        <td className="text-muted text-sm">#{r.id}</td>
                        <td>
                          <span style={{
                            fontFamily: 'monospace', fontWeight: 700,
                            fontSize: '.8rem', color: '#1d4ed8',
                          }}>{r.name}</span>
                        </td>
                        <td className="text-sm text-muted">{r.description || '—'}</td>
                        <td>
                          <div style={{ display: 'flex', gap: '.2rem', flexWrap: 'wrap' }}>
                            {(r.permissions || []).slice(0, 4).map(p => (
                              <span key={p.id} style={{
                                fontSize: '.68rem', fontWeight: 600, padding: '.1em .4em',
                                borderRadius: 3, background: '#f1f5f9', color: '#475569',
                              }}>{p.name}</span>
                            ))}
                            {(r.permissions || []).length > 4 && (
                              <span style={{
                                fontSize: '.68rem', fontWeight: 600, padding: '.1em .4em',
                                borderRadius: 3, background: '#e2e8f0', color: '#64748b',
                              }}>+{r.permissions.length - 4} more</span>
                            )}
                            {(r.permissions || []).length === 0 && (
                              <span className="text-muted" style={{ fontSize: '.75rem' }}>No permissions</span>
                            )}
                          </div>
                        </td>
                        <td className="text-sm text-muted">{fmtDate(r.created_at)}</td>
                        <td>
                          <span className={`badge badge-${r.status === 'ACT' ? 'act' : 'inact'}`}>
                            {r.status === 'ACT' ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td>
                          <div className="actions-row">
                            {canWrite && (
                              <button className="btn-action" title="Edit" onClick={() => openEdit(r)}>
                                <i className="fa-solid fa-pen" />
                              </button>
                            )}
                            {canWrite && (r.status === 'ACT'
                              ? <button className="btn-action warning" title="Deactivate" onClick={() => toggleStatus(r)}>
                                  <i className="fa-solid fa-pause" />
                                </button>
                              : <button className="btn-action success" title="Activate" onClick={() => toggleStatus(r)}>
                                  <i className="fa-solid fa-play" />
                                </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            )}
        </div>

        <Pagination page={page} total={total} pageSize={PAGE_SIZE} onChange={setPage} />
      </div>

      {/* Create / Edit Modal */}
      <Modal
        show={modal}
        onClose={closeModal}
        title={editId ? `Edit Role #${editId}` : 'New Role'}
        size="lg"
        footer={
          <>
            <button className="btn btn-secondary" onClick={closeModal}>Cancel</button>
            <button className="btn btn-primary" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : editId ? 'Update Role' : 'Create Role'}
            </button>
          </>
        }
      >
        <div className="form-row">
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Role Name <span className="text-required">*</span></label>
            <input
              className="form-control mono"
              value={form.name}
              onChange={e => f('name', e.target.value.toUpperCase().replace(/\s+/g, '_'))}
              placeholder="e.g. MANAGER"
              disabled={!!editId}
            />
            {editId && <div className="form-hint">Role name cannot be changed after creation.</div>}
          </div>

          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Description</label>
            <input
              className="form-control"
              value={form.description}
              onChange={e => f('description', e.target.value)}
              placeholder="Brief description of this role's purpose"
            />
          </div>
        </div>

        <div className="form-group" style={{ marginTop: '.5rem' }}>
          <label className="form-label" style={{ marginBottom: '.5rem', display: 'block' }}>
            Permissions
            <span style={{ marginLeft: '.5rem', fontSize: '.75rem', fontWeight: 400, color: '#64748b' }}>
              ({form.permissionIds.length} selected)
            </span>
          </label>
          <PermissionGrid
            allPermissions={allPerms}
            selected={form.permissionIds}
            onChange={ids => f('permissionIds', ids)}
          />
        </div>
      </Modal>
    </div>
  );
}