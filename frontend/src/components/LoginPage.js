import React, { useState } from 'react';
import { Link } from 'react-router-dom';

function LoginPage({ role, onLogin, loading }) {
  const [username, setUsername] = useState(role === 'ADMIN' ? 'admin' : 'user');
  const [password, setPassword] = useState(role === 'ADMIN' ? 'admin123' : 'user123');
  const [error, setError] = useState('');

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    try {
      await onLogin({ role, username, password });
    } catch (err) {
      const msg = err.message || 'Login failed';
      if (msg.toLowerCase().includes('role mismatch')) {
        setError(role === 'ADMIN' ? 'You are on Admin Login. Use admin credentials or switch to User Login.' : 'You are on User Login. Use user credentials or switch to Admin Login.');
        return;
      }
      setError(msg);
    }
  };

  return (
    <div className="login-shell">
      <main className="auth-layout">
        <section className="auth-art">
          <span className="auth-badge">Smart Complaint Management</span>
          <h1>Resolve complaints with confidence, speed, and clarity.</h1>
          <p>
            Streamline handoffs between citizens and administrators through a single responsive workspace.
            Built for transparency, fast actions, and cleaner operations.
          </p>
          <div className="auth-points">
            <span>Smart triage</span>
            <span>Live status updates</span>
            <span>Role-based workflows</span>
          </div>
        </section>

        <section className="login-card-wrap">
          <div className="login-card">
            <h2>{role === 'ADMIN' ? 'Admin Login' : 'User Login'}</h2>
            <p className="login-subtitle">
              {role === 'ADMIN' ? 'Manage complaints and analytics.' : 'Raise complaints and track your issues.'}
            </p>
            <div className="login-switch-links">
              <Link to="/login/user" className={role === 'USER' ? 'active-link' : ''}>User Login</Link>
              <Link to="/login/admin" className={role === 'ADMIN' ? 'active-link' : ''}>Admin Login</Link>
            </div>
            <form onSubmit={submit} className="login-form">
              <label htmlFor="username">Username</label>
              <input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
              />

              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />

              <button disabled={loading} type="submit">
                {loading ? 'Logging in...' : 'Login'}
              </button>
              {error ? <p className="error-text">{error}</p> : null}
            </form>
            <p className="hint-text">
              Demo credentials: {role === 'ADMIN' ? 'admin / admin123' : 'user / user123'}
            </p>
          </div>
        </section>
      </main>
    </div>
  );
}

export default LoginPage;
