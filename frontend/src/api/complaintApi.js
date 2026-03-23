const BASE_URL = '/api';

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function request(path, options = {}, token) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    const text = await response.text();
    let message = text || `Request failed with status ${response.status}`;
    try {
      const parsed = JSON.parse(text);
      if (parsed?.error) {
        message = parsed.error;
      }
    } catch (_err) {
      // Keep original text when backend response is not JSON.
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export function getHealth() {
  return request('/health');
}

export function login(role, username, password) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ role, username, password })
  });
}

export function getComplaints(token) {
  return request('/complaints', {}, token);
}

export function createComplaint(token, payload) {
  return request('/complaints', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateComplaintStatus(token, id, status) {
  return request(`/complaints/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  }, token);
}

export function getAnalytics(token) {
  return request('/analytics', {}, token);
}
