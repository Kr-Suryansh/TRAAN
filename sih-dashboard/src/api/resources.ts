// ============================================================
// sih-dashboard · src/api/resources.ts
// Endpoints: GET /resources, GET /resources/{id}, PATCH /resources/{id}
// ============================================================

import { apiClient, MOCK_MODE } from './client';
import { MOCK_RESOURCES } from '../mocks';
import type { Resource } from '../types';

export interface ResourceListParams {
  category?: string;
  status?: string;
  district?: string;
}

export async function fetchResources(params?: ResourceListParams): Promise<Resource[]> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 300));
    return [...MOCK_RESOURCES];
  }
  const res = await apiClient.get<Resource[]>('/resources', { params });
  return res.data;
}

export async function fetchResource(resourceId: string): Promise<Resource> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 200));
    const r = MOCK_RESOURCES.find((res) => res.resource_id === resourceId);
    if (!r) throw new Error(`Resource ${resourceId} not found`);
    return r;
  }
  const res = await apiClient.get<Resource>(`/resources/${resourceId}`);
  return res.data;
}

export async function patchResource(
  resourceId: string,
  update: Partial<Pick<Resource, 'quantity_available' | 'status'>>
): Promise<Resource> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 300));
    const r = MOCK_RESOURCES.find((res) => res.resource_id === resourceId);
    if (!r) throw new Error(`Resource ${resourceId} not found`);
    return { ...r, ...update };
  }
  const res = await apiClient.patch<Resource>(`/resources/${resourceId}`, update);
  return res.data;
}
