import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import {
  ApiError,
  createComplaint,
  getAnalytics,
  getComplaints,
  getHealth,
  login,
  updateComplaintStatus
} from './api/complaintApi';
import AdminDashboard from './components/AdminDashboard';
import LoginPage from './components/LoginPage';
import ProtectedRoute from './components/ProtectedRoute';
import ToastStack from './components/ToastStack';
import UserDashboard from './components/UserDashboard';
import './App.css';

function loadStoredAuth() {
  try {
    const raw = window.localStorage.getItem('scs_auth');
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw);
    if (!parsed.token || !parsed.role || !parsed.expiresAt) {
      return null;
    }
    if (parsed.expiresAt * 1000 <= Date.now()) {
      return null;
    }
    return parsed;
  } catch (_err) {
    return null;
  }
}

function AppContainer() {
  const navigate = useNavigate();
  const [auth, setAuth] = useState(() => loadStoredAuth());
  const [complaints, setComplaints] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [authLoading, setAuthLoading] = useState(false);
  const [toasts, setToasts] = useState([]);

  const pushToast = useCallback((message, type = 'info') => {
    const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    setToasts((prev) => [...prev, { id, message, type }]);
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((item) => item.id !== id));
    }, 3000);
  }, []);

  const logout = useCallback((reason) => {
    window.localStorage.removeItem('scs_auth');
    setAuth(null);
    setComplaints([]);
    setAnalytics(null);
    if (reason) {
      pushToast(reason, 'warning');
    }
    navigate('/login/user', { replace: true });
  }, [navigate, pushToast]);

  useEffect(() => {
    if (!auth) {
      window.localStorage.removeItem('scs_auth');
      return;
    }
    window.localStorage.setItem('scs_auth', JSON.stringify(auth));
  }, [auth]);

  useEffect(() => {
    if (!auth?.expiresAt) {
      return undefined;
    }

    const remainingMs = auth.expiresAt * 1000 - Date.now();
    if (remainingMs <= 0) {
      logout('Session expired. Please login again.');
      return undefined;
    }

    const timer = window.setTimeout(() => {
      logout('Token expired. Logged out for safety.');
    }, remainingMs);

    return () => window.clearTimeout(timer);
  }, [auth, logout]);

  const loadData = useCallback(async () => {
    if (!auth?.token) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      await getHealth();
      const list = await getComplaints(auth.token);
      setComplaints(list);
      if (auth.role === 'ADMIN') {
        const metrics = await getAnalytics(auth.token);
        setAnalytics(metrics);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        logout('Session invalid. Please login again.');
        return;
      }
      pushToast(err.message || 'Failed to load dashboard data.', 'error');
    } finally {
      setLoading(false);
    }
  }, [auth, logout, pushToast]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const loginUser = async ({ role, username, password }) => {
    try {
      setAuthLoading(true);
      const result = await login(role, username, password);
      const nextAuth = {
        token: result.token,
        role: result.role,
        username: result.username,
        expiresAt: result.expiresAt
      };
      setAuth(nextAuth);
      pushToast('Login successful.', 'success');
      navigate(result.role === 'ADMIN' ? '/admin' : '/user', { replace: true });
    } catch (err) {
      throw new Error(err.message || 'Invalid credentials');
    } finally {
      setAuthLoading(false);
    }
  };

  const handleCreateComplaint = async (payload) => {
    try {
      await createComplaint(auth.token, payload);
      pushToast('Complaint created.', 'success');
      await loadData();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        logout('Session expired. Please login again.');
        return;
      }
      pushToast(err.message || 'Could not create complaint.', 'error');
    }
  };

  const handleStatusChange = async (id, status) => {
    try {
      await updateComplaintStatus(auth.token, id, status);
      pushToast(`Complaint ${id} updated to ${status}.`, 'success');
      await loadData();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        logout('Session expired. Please login again.');
        return;
      }
      pushToast(err.message || 'Failed to update status.', 'error');
    }
  };

  const defaultRoute = useMemo(() => {
    if (!auth) {
      return '/login/user';
    }
    return auth.role === 'ADMIN' ? '/admin' : '/user';
  }, [auth]);

  return (
    <>
      <Routes>
        <Route path="/login/user" element={<LoginPage role="USER" onLogin={loginUser} loading={authLoading} />} />
        <Route path="/login/admin" element={<LoginPage role="ADMIN" onLogin={loginUser} loading={authLoading} />} />
        <Route
          path="/user"
          element={(
            <ProtectedRoute auth={auth} role="USER">
              <UserDashboard
                auth={auth}
                complaints={complaints}
                loading={loading}
                onCreateComplaint={handleCreateComplaint}
                onLogout={() => logout('Logged out successfully.')}
              />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/admin"
          element={(
            <ProtectedRoute auth={auth} role="ADMIN">
              <AdminDashboard
                auth={auth}
                complaints={complaints}
                analytics={analytics}
                loading={loading}
                onStatusChange={handleStatusChange}
                onLogout={() => logout('Logged out successfully.')}
              />
            </ProtectedRoute>
          )}
        />
        <Route path="*" element={<Navigate to={defaultRoute} replace />} />
      </Routes>
      <ToastStack toasts={toasts} />
    </>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AppContainer />
    </BrowserRouter>
  );
}

export default App;
