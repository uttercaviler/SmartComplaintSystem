import React, { useMemo, useState } from 'react';
import '../styles/ComplaintList.css';

const PAGE_SIZE = 5;

function ComplaintList({ complaints, loading, role, onStatusChange }) {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sortOrder, setSortOrder] = useState('LATEST');
  const [page, setPage] = useState(1);

  const filteredComplaints = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();

    const filtered = complaints.filter((item) => {
      const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter;
      const haystack = `${item.title} ${item.description} ${item.userName} ${item.category || ''} ${item.priority || ''} ${item.location || ''}`.toLowerCase();
      const matchesSearch = !term || haystack.includes(term);
      return matchesStatus && matchesSearch;
    });

    filtered.sort((a, b) => {
      const aTime = new Date(a.createdAt).getTime();
      const bTime = new Date(b.createdAt).getTime();
      return sortOrder === 'LATEST' ? bTime - aTime : aTime - bTime;
    });

    return filtered;
  }, [complaints, searchTerm, statusFilter, sortOrder]);

  const totalPages = Math.max(1, Math.ceil(filteredComplaints.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filteredComplaints.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const changePage = (nextPage) => {
    if (nextPage < 1 || nextPage > totalPages) {
      return;
    }
    setPage(nextPage);
  };

  if (loading) {
    return (
      <div className="skeleton-list">
        {Array.from({ length: 5 }).map((_, index) => (
          <div key={index} className="skeleton-row" />
        ))}
      </div>
    );
  }

  return (
    <section className="complaint-list-wrap">
      <div className="filter-toolbar">
        <input
          placeholder="Search title, description, user"
          value={searchTerm}
          onChange={(e) => {
            setSearchTerm(e.target.value);
            setPage(1);
          }}
        />
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(1);
          }}
        >
          <option value="ALL">All Status</option>
          <option value="OPEN">OPEN</option>
          <option value="IN_PROGRESS">IN_PROGRESS</option>
          <option value="RESOLVED">RESOLVED</option>
          <option value="REJECTED">REJECTED</option>
        </select>
        <select value={sortOrder} onChange={(e) => setSortOrder(e.target.value)}>
          <option value="LATEST">Latest first</option>
          <option value="OLDEST">Oldest first</option>
        </select>
      </div>

      {!pageItems.length ? <p className="empty-list">No complaints matched your filters.</p> : null}

      {pageItems.map((item) => (
        <article key={item.id} className="complaint-row">
          <div className="complaint-main">
            <h3>{item.title}</h3>
            <p>{item.description}</p>
            <div className="detail-chip-row">
              {item.category ? <span className="meta-chip chip-category">{item.category}</span> : null}
              {item.priority ? <span className={`meta-chip chip-priority chip-priority-${item.priority.toLowerCase()}`}>{item.priority}</span> : null}
              {item.location ? <span className="meta-chip chip-location">{item.location}</span> : null}
              {item.preferredContact ? <span className="meta-chip chip-contact">{item.preferredContact}</span> : null}
            </div>
            <div className="meta-row">
              <span className={`status-badge status-${item.status.toLowerCase()}`}>{item.status}</span>
              <span>{new Date(item.createdAt).toLocaleString()}</span>
              {role === 'ADMIN' ? <span>User: {item.userName}</span> : null}
            </div>
          </div>
          {role === 'ADMIN' ? (
            <div className="status-actions">
              <select defaultValue={item.status} onChange={(e) => onStatusChange(item.id, e.target.value)}>
                <option value="OPEN">OPEN</option>
                <option value="IN_PROGRESS">IN_PROGRESS</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="REJECTED">REJECTED</option>
              </select>
            </div>
          ) : null}
        </article>
      ))}

      <div className="pagination-bar">
        <button onClick={() => changePage(currentPage - 1)} disabled={currentPage === 1}>Prev</button>
        <span>
          Page {currentPage} of {totalPages}
        </span>
        <button onClick={() => changePage(currentPage + 1)} disabled={currentPage === totalPages}>Next</button>
      </div>
    </section>
  );
}

export default ComplaintList;
