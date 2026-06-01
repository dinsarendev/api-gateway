import { useCallback, useEffect, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';

const PAGE_SIZE = 15;

const AVAILABLE_ROLES = ['SUPER_ADMIN', 'OPERATOR', 'VIEWER'];

const EMPTY_FORM = {
  username: '', password: '', email: '', full_name: '', roles: [],
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
          ? '0 users'
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

// ── Main component ─────────────────────────────────────────────────────────
export default function Users() {
  const toast = useToast();

  // data
  const [users,   setUsers]   = useState([]);
  const [total,   setTotal]   = useState(0);

  // filters
  const [status,  setStatus]  = useState('ACT');
  const [search,  setSearch]  = useState('');
  const [page,    setPage]    = useState(0);

  // UI state
  const [loading, setLoading] = useState(true);
  const [modal,   setModal]   = useState(false);
  const [form,    setForm]    = useState(EMPTY_FORM);
  const [editId,  setEditId]  = useState(null);
  const [saving,  setSaving]  = useState(false);

  // ── Load ──────────────────────────────────────────────────────────────────
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await API.getUsers({ status, search, page, size: PAGE_SIZE });
      setUsers(res.data);
      setTotal(res.total);
    } catch {
      toast.error('Failed to load users');
    } finally {
      setLoading(false);
    }
  }, [status, search, page]);

  useEffect(() => { load(); }, [load]);

  // Reset page when filters change (but not page itself)
  useEffect(() => { setPage(0); }, [status, search]);

  // ── Modal helpers ─────────────────────────────────────────────────────────
  const openCreate = () => {
    setEditId(null);
    setForm(EMPTY_FORM);
    setModal(true);
  };

  const openEdit = u => {
    setEditId(u.id);
    setForm({
      username:  u.username  || '',
      password:  '',
      email:     u.email     || '',
      full_name: u.full_name || '',
      roles:     u.roles     || [],
    });
    setModal(true);
  };

  const closeModal = () => { setModal(false); setSaving(false); };

  const f = (key, val) => setForm(prev => ({ ...prev, [key]: val }));

  const toggleRole = role => {
    setForm(prev => ({
      ...prev,
      roles: prev.roles.includes(role)
        ? prev.roles.filter(r => r !== role)
        : [...prev.roles, role],
    }));
  };

  // ── Save ──────────────────────────────────────────────────────────────────
  const save = async () => {
    if (!form.username.trim()) return toast.warn('Username is required.');
    if (!editId && !form.password.trim()) return toast.warn('Password is required for new users.');
    if (form.roles.length === 0) return toast.warn('Assign at least one role.');

    const payload = {
      username:  form.username,
      password:  form.password || undefined,
      email:     form.email    || null,
      full_name: form.full_name || null,
      roles:     form.roles,
    };

    setSaving(true);
    try {
      if (editId) {
        await API.updateUser(editId, payload);
        toast.success('User updated');
      } else {
        await API.createUser(payload);
        toast.success('User created');
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
  const toggleStatus = async (u) => {
    const next = u.status === 'ACT' ? 'INACT' : 'ACT';
    const label = next === 'ACT' ? 'activate' : 'deactivate';
    if (!window.confirm(`${label.charAt(0).toUpperCase() + label.slice(1)} user "${u.username}"?`)) return;
    try {
      await API.updateUserStatus(u.id, next);
      toast.success(`User ${label}d`);
      load();
    } catch { toast.error(`Failed to ${label} user`); }
  };

  // ── Delete ────────────────────────────────────────────────────────────────
  const remove = async (u) => {
    if (!window.confirm(`Deactivate user "${u.username}"? This will set status to Inactive.`)) return;
    try {
      await API.deleteUser(u.id);
      toast.success('User deactivated');
      load();
    } catch { toast.error('Delete failed'); }
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
          placeholder="Search username, email, name…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />

        <div className="ms-auto">
          <button className="btn btn-primary btn-sm" onClick={openCreate}>
            <i className="fa-solid fa-plus" /> New User
          </button>
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
                    <th>ID</th>
                    <th>Username</th>
                    <th>Full Name</th>
                    <th>Email</th>
                    <th>Roles</th>
                    <th>Last Login</th>
                    <th>Status</th>
                    <th style={{ width: 120 }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.length === 0
                    ? (
                      <tr>
                        <td colSpan={8}>
                          <div className="empty-state">
                            <i className="fa-solid fa-users" />No users found
                          </div>
                        </td>
                      </tr>
                    )
                    : users.map(u => (
                      <tr key={u.id}>
                        <td className="text-muted text-sm">#{u.id}</td>
                        <td style={{ fontWeight: 600 }}>{u.username}</td>
                        <td>{u.full_name || <span className="text-muted">—</span>}</td>
                        <td className="text-sm text-muted">{u.email || '—'}</td>
                        <td>
                          <div style={{ display: 'flex', gap: '.25rem', flexWrap: 'wrap' }}>
                            {(u.roles || []).map(r => (
                              <span key={r} style={{
                                fontSize: '.7rem', fontWeight: 700, padding: '.15em .5em',
                                borderRadius: 4, background: r === 'SUPER_ADMIN' ? '#fef3c7' : '#e0f2fe',
                                color: r === 'SUPER_ADMIN' ? '#92400e' : '#0369a1',
                              }}>{r}</span>
                            ))}
                          </div>
                        </td>
                        <td className="text-sm text-muted">{fmtDate(u.last_login_at)}</td>
                        <td>
                          <span className={`badge badge-${u.status === 'ACT' ? 'act' : 'inact'}`}>
                            {u.status === 'ACT' ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td>
                          <div className="actions-row">
                            <button className="btn-action" title="Edit" onClick={() => openEdit(u)}>
                              <i className="fa-solid fa-pen" />
                            </button>
                            {u.status === 'ACT'
                              ? <button className="btn-action warning" title="Deactivate" onClick={() => toggleStatus(u)}>
                                  <i className="fa-solid fa-pause" />
                                </button>
                              : <button className="btn-action success" title="Activate" onClick={() => toggleStatus(u)}>
                                  <i className="fa-solid fa-play" />
                                </button>
                            }
                            <button className="btn-action danger" title="Delete" onClick={() => remove(u)}>
                              <i className="fa-solid fa-trash" />
                            </button>
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
        title={editId ? `Edit User #${editId}` : 'New User'}
        size="md"
        footer={
          <>
            <button className="btn btn-secondary" onClick={closeModal}>Cancel</button>
            <button className="btn btn-primary" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : editId ? 'Update User' : 'Create User'}
            </button>
          </>
        }
      >
        <div className="form-row">
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Username <span className="text-required">*</span></label>
            <input
              className="form-control mono"
              value={form.username}
              onChange={e => f('username', e.target.value)}
              placeholder="e.g. john.doe"
              disabled={!!editId}
            />
            {editId && <div className="form-hint">Username cannot be changed after creation.</div>}
          </div>

          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">
              Password {editId
                ? <span className="text-muted" style={{ fontWeight: 400, fontSize: '.78rem' }}>(leave blank to keep current)</span>
                : <span className="text-required">*</span>}
            </label>
            <input
              className="form-control"
              type="password"
              value={form.password}
              onChange={e => f('password', e.target.value)}
              placeholder={editId ? 'Leave blank to keep current password' : 'Min 6 characters'}
              autoComplete="new-password"
            />
          </div>
        </div>

        <div className="form-row">
          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Full Name</label>
            <input
              className="form-control"
              value={form.full_name}
              onChange={e => f('full_name', e.target.value)}
              placeholder="Display name"
            />
          </div>

          <div className="form-group" style={{ gridColumn: 'span 2' }}>
            <label className="form-label">Email</label>
            <input
              className="form-control"
              type="email"
              value={form.email}
              onChange={e => f('email', e.target.value)}
              placeholder="user@example.com"
            />
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">Roles <span className="text-required">*</span></label>
          <div style={{ display: 'flex', gap: '.75rem', flexWrap: 'wrap', paddingTop: '.25rem' }}>
            {AVAILABLE_ROLES.map(role => (
              <label key={role} style={{ display: 'flex', alignItems: 'center', gap: '.4rem',
                cursor: 'pointer', fontSize: '.875rem' }}>
                <input
                  type="checkbox"
                  checked={form.roles.includes(role)}
                  onChange={() => toggleRole(role)}
                  style={{ cursor: 'pointer' }}
                />
                <span style={{
                  fontWeight: 600,
                  color: role === 'SUPER_ADMIN' ? '#92400e' : role === 'OPERATOR' ? '#0369a1' : '#374151',
                }}>{role}</span>
              </label>
            ))}
          </div>
          <div className="form-hint">Select one or more roles to assign.</div>
        </div>
      </Modal>
    </div>
  );
}