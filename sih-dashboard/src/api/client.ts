// ============================================================
// sih-dashboard · src/api/client.ts
// Base axios instance with auth interceptor.
// Phase 2: Replace MOCK_MODE=true with MOCK_MODE=false and set VITE_API_BASE_URL.
// ============================================================

import axios from 'axios';

// Phase 2 toggle: set VITE_MOCK_MODE=false in .env to use real backend
export const MOCK_MODE = import.meta.env.VITE_MOCK_MODE !== 'false';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000/api/v1';

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

// Attach authority access_token from sessionStorage
apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 — token expired or invalid → force re-login
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('access_token');
      sessionStorage.removeItem('authority_user');
      // Dispatch a custom event so AuthContext can react
      window.dispatchEvent(new CustomEvent('auth:expired'));
    }
    return Promise.reject(error);
  }
);
