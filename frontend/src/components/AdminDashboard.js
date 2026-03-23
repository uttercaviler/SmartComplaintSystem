import React from 'react';
import ComplaintList from './ComplaintList';

function AdminDashboard({ auth, complaints, analytics, loading, onStatusChange, onLogout }) {
  const cards = [
    { label: 'Total', value: analytics?.total ?? 0 },
    { label: 'Open', value: analytics?.OPEN ?? 0 },
    { label: 'In Progress', value: analytics?.IN_PROGRESS ?? 0 },
    { label: 'Resolved', value: analytics?.RESOLVED ?? 0 },
    { label: 'Rejected', value: analytics?.REJECTED ?? 0 }
  ];

  return (
    <div className="admin-layout">
      <aside className="sidebar">
        <h2>SCS Admin</h2>
        <nav>
          <a href="#analytics">Analytics</a>
          <a href="#complaints">Complaints</a>
        </nav>
      </aside>

      <main className="admin-main">
        <div className="admin-container">
          <header className="top-nav">
            <div>
              <h1>Admin Dashboard</h1>
              <p>Signed in as {auth.username}</p>
            </div>
            <button className="ghost-btn" onClick={onLogout}>Logout</button>
          </header>

          <section id="analytics" className="card-grid card-grid-admin">
            {cards.map((card) => (
              <article key={card.label} className="metric-card">
                <h3>{card.label}</h3>
                <p>{card.value}</p>
              </article>
            ))}
          </section>

          <section id="complaints" className="panel">
            <h2>All Complaints</h2>
            <ComplaintList
              complaints={complaints}
              loading={loading}
              role="ADMIN"
              onStatusChange={onStatusChange}
            />
          </section>
        </div>
      </main>
    </div>
  );
}

export default AdminDashboard;
