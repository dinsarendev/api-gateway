import { useEffect, useState } from 'react';
import { API } from '../api/gateway';
import Modal from '../components/Modal';
import { useToast } from '../context/ToastContext';

const EMPTY = { name: '', introspection_uri: '', client_id: '', client_secret: '' };

export default function OAuth2Providers() {
  const toast = useToast();
  const [providers, setProviders] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [modal,     setModal]     = useState(false);
  const [form,      setForm]      = useState(EMPTY);
  const [editId,    setEditId]    = useState(null);
  const [saving,    setSaving]    = useState(false);

  const load = async () => {
    setLoading(true);
    try { setProviders(await API.getOAuth2Providers()); }
    catch { toast.error('Failed to load providers'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const openCreate = () => { setEditId(null); setForm(EMPTY); setModal(true); };
  const openEdit   = p  => {
    setEditId(p.id);
    setForm({ name: p.name, introspection_uri: p.introspection_uri, client_id: p.client_id, client_secret: '' });
    setModal(true);
  };
  const f = (k, v) => setForm(p => ({ ...p, [k]: v }));

  const save = async () => {
    if (!form.name.trim() || !form.introspection_uri.trim()) return toast.warn('Name and Introspection URI are required.');
    setSaving(true);
    try {
      if (editId) {
        await API.updateOAuth2Provider(editId, form);
        toast.success('Provider updated');
      } else {
        await API.createOAuth2Provider(form);
        toast.success('Provider created');
      }
      setModal(false);
      load();
    } catch(e) { toast.error('Failed: ' + (e.message || JSON.stringify(e))); }
    finally { setSaving(false); }
  };

  const remove = async (id, name) => {
    if (!window.confirm(`Delete provider "${name}"?`)) return;
    try { await API.deleteOAuth2Provider(id); toast.success('Provider deleted'); load(); }
    catch { toast.error('Delete failed'); }
  };

  return (
    <div>
      <div className="filter-bar">
        <div className="ms-auto">
          <button className="btn btn-primary btn-sm" onClick={openCreate}>
            <i className="fa-solid fa-plus" /> New Provider
          </button>
        </div>
      </div>

      <div className="card">
        <div className="table-wrap">
          {loading ? <div className="loading-center"><div className="spinner" /></div> : (
            <table>
              <thead>
                <tr><th>ID</th><th>Name</th><th>Introspection URI</th><th>Client ID</th><th>Status</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {providers.length === 0
                  ? <tr><td colSpan={6}><div className="empty-state"><i className="fa-solid fa-inbox" />No providers configured</div></td></tr>
                  : providers.map(p => (
                    <tr key={p.id}>
                      <td className="text-muted text-sm">#{p.id}</td>
                      <td className="fw-bold">{p.name}</td>
                      <td className="font-mono text-sm" style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                        title={p.introspection_uri}>{p.introspection_uri}</td>
                      <td className="text-sm">{p.client_id}</td>
                      <td><span className={`badge badge-${p.status === 'ACT' ? 'act' : 'inact'}`}>{p.status === 'ACT' ? 'Active' : 'Inactive'}</span></td>
                      <td>
                        <div className="actions-row">
                          <button className="btn-action" title="Edit" onClick={() => openEdit(p)}><i className="fa-solid fa-pen" /></button>
                          <button className="btn-action danger" title="Delete" onClick={() => remove(p.id, p.name)}><i className="fa-solid fa-trash" /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <Modal show={modal} onClose={() => setModal(false)} title={editId ? 'Edit OAuth2 Provider' : 'New OAuth2 Provider'}
        footer={<>
          <button className="btn btn-secondary" onClick={() => setModal(false)}>Cancel</button>
          <button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save Provider'}</button>
        </>}>
        <div className="form-group">
          <label className="form-label">Name <span className="text-required">*</span></label>
          <input className="form-control" value={form.name} onChange={e => f('name', e.target.value)} placeholder="e.g. Keycloak" disabled={!!editId} />
        </div>
        <div className="form-group">
          <label className="form-label">Introspection URI <span className="text-required">*</span></label>
          <input className="form-control mono" value={form.introspection_uri} onChange={e => f('introspection_uri', e.target.value)}
            placeholder="https://auth.example.com/oauth/introspect" />
          <div className="form-hint">RFC 7662 token introspection endpoint — called with Basic auth (client_id:secret).</div>
        </div>
        <div className="form-row">
          <div className="form-group">
            <label className="form-label">Client ID <span className="text-required">*</span></label>
            <input className="form-control" value={form.client_id} onChange={e => f('client_id', e.target.value)} placeholder="gateway-client" />
          </div>
          <div className="form-group">
            <label className="form-label">Client Secret {editId && <span className="text-muted text-sm">(leave blank to keep)</span>}</label>
            <input className="form-control" type="password" value={form.client_secret} onChange={e => f('client_secret', e.target.value)} placeholder="••••••••" />
          </div>
        </div>
      </Modal>
    </div>
  );
}