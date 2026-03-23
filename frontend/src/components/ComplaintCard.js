import React from 'react';
import '../styles/ComplaintCard.css';

function ComplaintCard({ complaint }) {
  return (
    <article className="complaint-card">
      <header>
        <h3>{complaint.title}</h3>
        <span className="status-pill">{complaint.status}</span>
      </header>
      <p className="meta">By {complaint.userName} on {new Date(complaint.createdAt).toLocaleString()}</p>
      <p>{complaint.description}</p>
    </article>
  );
}

export default ComplaintCard;
