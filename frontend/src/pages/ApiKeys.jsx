import { useEffect, useMemo, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import Pagination from '../components/Pagination';
import { useToast } from '../context/ToastContext';
import { useAuth, PERMS } from '../context/AuthContext';

const EMPTY     = { name: '', client_id: '', roles: '', permissions: '', expires_at: '' };
const PAGE_SIZE = 10;

export default function ApiKeys() {
  const toast    = useToast();
  const { can }  = useAuth();
  const canWrite = can(PERMS.SECURITY_WRITE);
  const [keys,    setKeys]    = useState([]);
  const [loading, setLoading] = useState(true);
  const [modal,   setModal]   = useState(false);
  const [form,    setForm]    = useState(EMPTY);
  const [saving,  setSaving]  = useState(false);
  const [created, setCreated] = useState(null);
  const [search,  setSearch]  = useState('');
  const [page,    setPage]    = useState(0);

  const load = async () => {
    setLoading(true);
    try { setKeys(await API.getApiKeys()); }
    catch { toast.error('Failed to load API keys'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);
  useEffect(() => { setPage(0); }, [search]);

  const filtered = useMemo(() =>
    keys.filter(k =>
      !search ||
      k.name?.toLowerCase().includes(search.toLowerCase()) ||
      k.client_id?.toLowerCase().includes(search.toLowerCase()) ||
      k.key_prefix?.toLowerCase().includes(search.toLowerCase())
    ), [keys, search]
  );

  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const f = (k, v) => setForm(p => ({ ...p, [k]: v }));

  const save = async () => {
    if (!form.name.trim()) return toast.warn('Name is required.');
    setSaving(true);
    try {
      const res = await API.createApiKey(form);
      setModal(false);
      setCreated(res.key);
      load();
    } catch(e) { toast.error('Failed: ' + (e.message || JSON.stringify(e))); }
    finally { setSaving(false); }
  };

  const revoke = async (id, name) => {
    if (!window.confirm(`Revoke key "${name}"? Any services using it will stop working.`)) return;
    try { await API.revokeApiKey(id); toast.success('Key revoked'); load(); }
    catch { toast.error('Revoke failed'); }
  };

  const fmt = dt => dt ? new Date(dt).toLocaleString() : '—';

  return (
    <div>
      {/* One-time key display */}
      {created && (
        <div className="card" style={{ marginBottom: '1rem', border: '2px solid #16a34a' }}>
          <div style={{ padding: '1rem' }}>
            <div style={{ fontWeight: 700, marginBottom: '.5rem', color: '#16a34a' }}>
              <i className="fa-solid fa-key" style={{ marginRight: '.5rem' }} />
              API Key Created — copy it now, it won&apos;t be shown again
            </div>
            <code style={{ background: '#f1f5f9', padding: '.5rem .75rem', borderRadius: 6,
              display: 'block', fontFamily: 'monospace', wordBreak: 'break-all' }}>
              {created}
            </code>
            <button className="btn btn-secondary btn-sm" style={{ marginTop: '.75rem' }}
              onClick={() => { navigator.clipboard?.writeText(created); toast.success('Copied!'); }}>
              <i className="fa-solid fa-copy" /> Copy
            </button>
            <button className="btn btn-secondary btn-sm" style={{ marginTop: '.75rem', marginLeft: '.5rem' }}
              onClick={() => setCreated(null)}>
              Dismiss
            </button>
          </div>
        </div>
      )}

      <div className="filter-bar">
        <input className="form-control" style={{ width: 220 }} placeholder="Search name, client ID…"
          value={search} onChange={e => setSearch(e.target.value)} />
        {canWrite && (
          <div className="ms-auto">
            <button className="btn btn-primary btn-sm" onClick={() => { setForm(EMPTY); setModal(true); }}>
              <i className="fa-solid fa-plus" /> New API Key
            </button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="table-wrap">
          {loading ? <div className="loading-center"><div className="spinner" /></div> : (
            <table>
              <thead>
                <tr><th>ID</th><th>Name</th><th>Prefix</th><th>Client ID</th><th>Roles</th><th>Permissions</th><th>Expires</th><th>Last Used</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {paged.length === 0
                  ? <tr><td colSpan={9}><div className="empty-state"><i className="fa-solid fa-inbox" />{keys.length === 0 ? 'No API keys' : 'No results'}</div></td></tr>
                  : paged.map(k => (
                    <tr key={k.id}>
                      <td className="text-muted text-sm">#{k.id}</td>
                      <td className="fw-bold">{k.name}</td>
                      <td className="font-mono text-sm">{k.key_prefix}…</td>
                      <td className="text-sm text-muted">{k.client_id || '—'}</td>
                      <td className="text-sm">{k.roles || '—'}</td>
                      <td className="text-sm">{k.permissions || '—'}</td>
                      <td className="text-sm text-muted">{fmt(k.expires_at)}</td>
                      <td className="text-sm text-muted">{fmt(k.last_used_at)}</td>
                      <td>
                        {canWrite && (
                          <button className="btn-action danger" title="Revoke" onClick={() => revoke(k.id, k.name)}>
                            <i className="fa-solid fa-ban" />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          )}
        </div>
        <Pagination page={page} total={filtered.length} pageSize={PAGE_SIZE} onChange={setPage} label="keys" />
      </div>

      <Modal show={modal} onClose={() => setModal(false)} title="New API Key"
        footer={<>
          <button className="btn btn-secondary" onClick={() => setModal(false)}>Cancel</button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? 'Creating…' : 'Create Key'}</button>
        </>}>
        <div className="form-group">
          <label className="form-label">Name <span className="text-required">*</span></label>
          <input className="form-control" value={form.name}
            onChange={e => f('name', e.target.value)} placeholder="e.g. Mobile App v2" />
        </div>
        <div className="form-group">
          <label className="form-label">Client ID</label>
          <input className="form-control" value={form.client_id}
            onChange={e => f('client_id', e.target.value)} placeholder="optional identifier" />
        </div>
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Roles</label>
            <input className="form-control" value={form.roles}
              onChange={e => f('roles', e.target.value)} placeholder="ADMIN,USER (comma-separated)" />
          </div>
          <div className="form-group">
            <label className="form-label">Permissions</label>
            <input className="form-control" value={form.permissions}
              onChange={e => f('permissions', e.target.value)} placeholder="read:orders,write:orders" />
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">Expires At</label>
          <input className="form-control" type="datetime-local" value={form.expires_at}
            onChange={e => f('expires_at', e.target.value)} />
          <div className="form-hint">Leave empty for non-expiring key.</div>
        </div>
      </Modal>
    </div>
  );
}