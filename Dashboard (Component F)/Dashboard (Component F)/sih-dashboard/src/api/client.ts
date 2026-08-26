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

// Handle 401 — token expired or invalid → attempt refresh, then force re-login if failed
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      const refreshToken = sessionStorage.getItem('refresh_token');
      
      if (refreshToken) {
        try {
          const res = await axios.post(`${BASE_URL}/auth/authority/refresh`, {
            refresh_token: refreshToken
          });
          const { access_token, refresh_token } = res.data;
          
          sessionStorage.setItem('access_token', access_token);
          sessionStorage.setItem('refresh_token', refresh_token);
          
          originalRequest.headers.Authorization = `Bearer ${access_token}`;
          return apiClient(originalRequest);
        } catch (refreshError) {
          // Refresh failed
        }
      }
      
      sessionStorage.removeItem('access_token');
      sessionStorage.removeItem('refresh_token');
      sessionStorage.removeItem('authority_user');
      window.dispatchEvent(new CustomEvent('auth:expired'));
    }
    return Promise.reject(error);
  }
);
