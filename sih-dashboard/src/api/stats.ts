// ============================================================
// sih-dashboard · src/api/stats.ts
// Endpoints: GET /stats/summary, GET /situation-brief
// ============================================================

import { apiClient, MOCK_MODE } from './client';
import { MOCK_STATS, MOCK_SITUATION_BRIEF } from '../mocks';
import type { StatsSummary, SituationBriefPayload } from '../types';

export async function fetchStatsSummary(): Promise<StatsSummary> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 300));
    return { ...MOCK_STATS };
  }
  const res = await apiClient.get<StatsSummary>('/stats/summary');
  return res.data;
}

export async function fetchSituationBrief(): Promise<SituationBriefPayload> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 300));
    return { ...MOCK_SITUATION_BRIEF };
  }
  const res = await apiClient.get<SituationBriefPayload>('/situation-brief');
  return res.data;
}

export async function refreshSituationBrief(): Promise<void> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 800));
    return;
  }
  await apiClient.post('/situation-brief/refresh');
}
