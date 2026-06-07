export default function Pagination({ page, total, pageSize, onChange, label = 'items' }) {
  const totalPages = Math.ceil(total / pageSize);
  if (totalPages <= 1) return null;

  const pages = [];
  const start = Math.max(0, page - 2);
  const end   = Math.min(totalPages - 1, page + 2);
  for (let i = start; i <= end; i++) pages.push(i);

  const from = total === 0 ? 0 : page * pageSize + 1;
  const to   = Math.min((page + 1) * pageSize, total);

  return (
    <div className="pagination">
      <span className="page-info">
        {total === 0 ? `0 ${label}` : `${from}–${to} of ${total}`}
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
