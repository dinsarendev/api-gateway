import { createContext, useCallback, useContext, useState } from 'react';

const Ctx = createContext(null);

const ICONS = {
  success: { icon: 'fa-circle-check',        color: '#22c55e' },
  error:   { icon: 'fa-circle-xmark',         color: '#ef4444' },
  warning: { icon: 'fa-triangle-exclamation', color: '#f59e0b' },
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const show = useCallback((msg, type = 'success') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, msg, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000);
  }, []);

  const remove = id => setToasts(prev => prev.filter(t => t.id !== id));

  return (
    <Ctx.Provider value={{ show, success: m => show(m,'success'), error: m => show(m,'error'), warn: m => show(m,'warning') }}>
      {children}
      <div className="toast-container">
        {toasts.map(t => {
          const { icon, color } = ICONS[t.type] || ICONS.success;
          return (
            <div key={t.id} className="toast">
              <i className={`fa-solid ${icon}`} style={{ color }} />
              <span style={{ flex: 1 }}>{t.msg}</span>
              <button onClick={() => remove(t.id)} style={{ background:'none',border:'none',fontSize:'1.1rem',color:'#94a3b8' }}>×</button>
            </div>
          );
        })}
      </div>
    </Ctx.Provider>
  );
}

export const useToast = () => useContext(Ctx);
