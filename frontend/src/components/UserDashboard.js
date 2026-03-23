import React from 'react';
import CreateComplaint from './CreateComplaint';
import ComplaintList from './ComplaintList';

function UserDashboard({ auth, complaints, loading, onCreateComplaint, onLogout }) {
  const counts = {
    total: complaints.length,
    open: complaints.filter((c) => c.status === 'OPEN').length,
    inProgress: complaints.filter((c) => c.status === 'IN_PROGRESS').length,
    resolved: complaints.filter((c) => c.status === 'RESOLVED').length
  };

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <header className="top-nav">
          <div>
            <h1>User Dashboard</h1>
            <p>Welcome, {auth.username}</p>
          </div>
          <button className="ghost-btn" onClick={onLogout}>Logout</button>
        </header>

        <div className="card-grid">
          <article className="metric-card"><h3>Total</h3><p>{counts.total}</p></article>
          <article className="metric-card"><h3>Open</h3><p>{counts.open}</p></article>
          <article className="metric-card"><h3>In Progress</h3><p>{counts.inProgress}</p></article>
          <article className="metric-card"><h3>Resolved</h3><p>{counts.resolved}</p></article>
        </div>

        <div className="user-workspace">
          <section className="panel complaint-submit-panel">
            <div className="panel-heading-row">
              <h2>Create Complaint</h2>
              <span className="panel-subtext">Structured submission for faster resolution</span>
            </div>
            <CreateComplaint onCreate={onCreateComplaint} />
          </section>
          <section className="panel complaint-feed-panel">
            <div className="panel-heading-row">
              <h2>My Complaints</h2>
              <span className="panel-subtext">Track updates in real time</span>
            </div>
            <ComplaintList complaints={complaints} loading={loading} role="USER" />
          </section>
        </div>
      </div>
    </div>
  );
}

export default UserDashboard;
