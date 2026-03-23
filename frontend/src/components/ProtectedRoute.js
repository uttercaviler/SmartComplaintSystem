import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';

function ProtectedRoute({ auth, role, children }) {
  const location = useLocation();

  if (!auth?.token) {
    const loginPath = role === 'ADMIN' ? '/login/admin' : '/login/user';
    return <Navigate to={loginPath} replace state={{ from: location.pathname }} />;
  }

  if (role && auth.role !== role) {
    const fallback = auth.role === 'ADMIN' ? '/admin' : '/user';
    return <Navigate to={fallback} replace />;
  }

  return children;
}

export default ProtectedRoute;
