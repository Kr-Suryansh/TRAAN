// ============================================================
// sih-dashboard · src/api/auth.ts
// Endpoint: POST /api/v1/auth/authority/login
// ============================================================

import { apiClient, MOCK_MODE } from './client';
import type { LoginResponse } from '../types';

export async function loginAuthority(email: string, password: string): Promise<LoginResponse> {
  if (MOCK_MODE) {
    // TEMP Phase 1 mock — remove when backend is ready
    await new Promise((r) => setTimeout(r, 600));
    if (email === 'demo@sih.gov.in' && password === 'demo1234') {
      return {
        access_token: 'mock_access_token_abc123',
        refresh_token: 'mock_refresh_token_def456',
        user: {
          user_id: 'usr-001',
          name: 'Arjun Mehta',
          role: 'DDMA',
          agency: 'District Disaster Management Authority, Jaipur',
          email: 'demo@sih.gov.in',
        },
      };
    }
    throw { response: { data: { detail: 'Invalid credentials' } } };
  }
  const res = await apiClient.post<LoginResponse>('/auth/authority/login', { email, password });
  return res.data;
}
