import axios from 'axios';

const API_BASE = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const api = axios.create({ baseURL: API_BASE });

// Attach JWT to every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Auto-logout on 401
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

// ── Auth ──────────────────────────────────────────────────────────────────
export const authApi = {
  register: (data) => api.post('/api/auth/register', data),
  login: (data) => api.post('/api/auth/login', data),
};

// ── Uploads ───────────────────────────────────────────────────────────────
export const uploadApi = {
  upload: (file, accountName) => {
    const form = new FormData();
    form.append('file', file);
    form.append('accountName', accountName);
    return api.post('/api/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  getStatus: (uploadId) => api.get(`/api/upload/status/${uploadId}`),
  getHistory: () => api.get('/api/upload/history'),
  deleteUpload: (uploadId) => api.delete(`/api/upload/${uploadId}`),
};

// ── Transactions ──────────────────────────────────────────────────────────
export const transactionApi = {
  getAll:          (year, month) => api.get('/api/transactions', { params: { year, month } }),
  add:             (data)        => api.post('/api/transactions', data),
  updateCategory:  (id, category) => api.put(`/api/transactions/${id}/category`, { category }),
  delete:          (id)          => api.delete(`/api/transactions/${id}`),
};

// ── Dashboard ─────────────────────────────────────────────────────────────
export const dashboardApi = {
  getSummary: (year, month) => api.get('/api/dashboard/summary', { params: { year, month } }),
  exportExcel: (year, month) => api.get('/api/dashboard/export/excel',
    { params: { year, month }, responseType: 'blob' }),
  exportPdf: (year, month) => api.get('/api/dashboard/export/pdf',
    { params: { year, month }, responseType: 'blob' }),
};

export default api;
