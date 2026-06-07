import { useEffect } from 'react';

export default function Modal({ show, onClose, title, size, children, footer }) {
  useEffect(() => {
    const handler = e => { if (e.key === 'Escape') onClose(); };
    if (show) document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [show, onClose]);

  if (!show) return null;

  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div className={`modal-box${size === 'lg' ? ' lg' : ''}`} onMouseDown={e => e.stopPropagation()}>
        <div className="modal-header">
          <h5>{title}</h5>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
}
