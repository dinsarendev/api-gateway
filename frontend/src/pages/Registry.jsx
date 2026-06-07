import { useEffect, useState } from 'react';
import { API } from '../api/gateway';
import { useToast } from '../context/ToastContext';

export default function Registry() {
  const toast = useToast();
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    API.serviceRegistry()
      .then(setList)
      .catch(() => toast.error('Failed to load registry'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading-center"><div className="spinner" /></div>;
  if (list.length === 0) return <div className="empty-state"><i className="fa-solid fa-server" />No service groups registered</div>;

  return (
    <div className="reg-grid">
      {list.map(g => {
        const allUp = g.up === g.instances && g.instances > 0;
        const health = g.instances === 0 ? 'oos' : allUp ? 'up' : 'down';
        return (
          <div key={g.code} className="reg-card">
            <div className="reg-card-header">
              <span><i className="fa-solid fa-server" style={{color:'#3b82f6',marginRight:'.5rem'}} />{g.code}</span>
              <span className={`badge badge-${health}`}>{health.toUpperCase()}</span>
            </div>
            <div className="reg-card-body">
              <div style={{marginBottom:'.75rem'}}>
                <div className="text-muted text-sm" style={{marginBottom:'.2rem'}}>TARGET URI</div>
                <span className="font-mono">
                  {g.lbEnabled && <span className="lb-badge" style={{marginRight:'.3rem'}}>lb://</span>}
                  {g.uri || '—'}
                </span>
              </div>
              <div className="count-row">
                <div className="count-item"><div className="num">{g.up}</div><div className="lbl">UP</div></div>
                <div className="count-item"><div className="num">{g.instances - g.up}</div><div className="lbl">DOWN</div></div>
                <div className="count-item"><div className="num">{g.instances}</div><div className="lbl">TOTAL</div></div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
