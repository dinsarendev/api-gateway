import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

const EMPTY    = { code: '', uri: '' };
const PAGE_SIZE = 10;

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
        {total === 0 ? '0 groups' : `${page * pageSize + 1}–${Math.min((page + 1) * pageSize, total)} of ${total}`}
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

export default function Groups() {
  const toast     = useToast();
  const navigate  = useNavigate();
  const { can }   = useAuth();
  const canWrite  = can(PERMS.GROUP_WRITE);

  const [groups,  setGroups]  = useState([]);
  const [loading, setLoading] = useState(true);
  const [modal,   setModal]   = useState(false);
  const [form,    setForm]    = useState(EMPTY);
  const [editId,  setEditId]  = useState(null);
  const [saving,  setSaving]  = useState(false);

  const [search, setSearch] = useState('');
  const [page,   setPage]   = useState(0);

  const load = async () => {
    setLoading(true);
    try { setGroups(await API.getGroups()); }
    catch { toast.error('Failed to load groups'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  // Reset to page 0 when search changes
  useEffect(() => { setPage(0); }, [search]);

  const filtered = useMemo(() =>
    groups.filter(g =>
      !search ||
      g.code?.toLowerCase().includes(search.toLowerCase()) ||
      g.uri?.toLowerCase().includes(search.toLowerCase())
    ),
    [groups, search]
  );

  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const openCreate = () => { setEditId(null); setForm(EMPTY); setModal(true); };
  const openEdit   = g  => { setEditId(g.id); setForm({ code: g.code, uri: g.uri || '' }); setModal(true); };
  const f = (k, v) => setForm(prev => ({ ...prev, [k]: v }));

  const viewRoutes = (code) => navigate(`/routes?group=${encodeURIComponent(code)}`);

  const save = async () => {
    if (!form.code || !form.uri) return toast.warn('Code and URI are required.');
    setSaving(true);
    try {
      const payload = { ...form, code: form.code.toUpperCase() };
      editId ? await API.updateGroup(editId, payload) : await API.createGroup(payload);
      toast.success(editId ? 'Group updated' : 'Group created');
      setModal(false); load();
    } catch(e) { toast.error('Save failed: ' + (e.message || JSON.stringify(e))); }
    finally { setSaving(false); }
  };

  const remove = async (id, code) => {
    if (!window.confirm(`Delete group "${code}"? All routes in this group will stop resolving.`)) return;
    try { await API.deleteGroup(id); toast.success('Group deleted'); load(); }
    catch { toast.error('Delete failed'); }
  };

  const fmt = dt => dt ? new Date(dt).toLocaleString() : '—';

  return (
    <div>
      <div className="filter-bar">
        <input
          className="form-control"
          style={{ width: '220px' }}
          placeholder="Search code or URI…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        {canWrite && (
          <div className="ms-auto">
            <button className="btn btn-primary btn-sm" onClick={openCreate}>
              <i className="fa-solid fa-plus" /> New Group
            </button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="table-wrap">
          {loading ? <div className="loading-center"><div className="spinner" /></div> : (
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Code</th>
                  <th>Target URI</th>
                  <th>Discovery</th>
                  <th>Status</th>
                  <th>Updated</th>
                  <th style={{ width: 148 }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {paged.length === 0
                  ? <tr><td colSpan={7}><div className="empty-state"><i className="fa-solid fa-inbox" />No groups found</div></td></tr>
                  : paged.map(g => (
                    <tr key={g.id}>
                      <td className="text-muted text-sm">#{g.id}</td>
                      <td className="fw-bold">{g.code}</td>
                      <td className="font-mono" style={{ fontSize: '.82rem' }}>{g.uri || '—'}</td>
                      <td>
                        {g.uri?.startsWith('lb://')
                          ? <span className="lb-badge"><i className="fa-solid fa-shuffle" /> Load Balanced</span>
                          : <span className="text-muted text-sm">Static</span>}
                      </td>
                      <td>
                        <span className={`badge badge-${g.status === 'ACT' ? 'act' : 'inact'}`}>
                          {g.status === 'ACT' ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="text-muted text-sm">{fmt(g.updatedAt)}</td>
                      <td>
                        <div className="actions-row">
                          <button className="btn-action" title="View Routes" onClick={() => viewRoutes(g.code)}>
                            <i className="fa-solid fa-route" />
                          </button>
                          {canWrite && (
                            <button className="btn-action" title="Edit" onClick={() => openEdit(g)}>
                              <i className="fa-solid fa-pen" />
                            </button>
                          )}
                          {canWrite && (
                            <button className="btn-action danger" title="Delete" onClick={() => remove(g.id, g.code)}>
                              <i className="fa-solid fa-trash" />
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

        <Pagination
          page={page}
          total={filtered.length}
          pageSize={PAGE_SIZE}
          onChange={setPage}
        />
      </div>

      <Modal
        show={modal}
        onClose={() => setModal(false)}
        title={editId ? 'Edit Group' : 'New Service Group'}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setModal(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : 'Save Group'}
            </button>
          </>
        }
      >
        <div className="form-group">
          <label className="form-label">Group Code <span className="text-required">*</span></label>
          <input
            className="form-control"
            value={form.code}
            onChange={e => f('code', e.target.value.toUpperCase())}
            placeholder="e.g. AUTH"
            disabled={!!editId}
          />
          <div className="form-hint">Uppercase identifier — matches service discovery service_id.</div>
        </div>
        <div className="form-group">
          <label className="form-label">Target URI <span className="text-required">*</span></label>
          <input
            className="form-control mono"
            value={form.uri}
            onChange={e => f('uri', e.target.value)}
            placeholder="http://host:port  or  lb://AUTH"
          />
          <div className="form-hint">Use <code>lb://&lt;CODE&gt;</code> to enable load-balanced service discovery.</div>
        </div>
      </Modal>
    </div>
  );
}
