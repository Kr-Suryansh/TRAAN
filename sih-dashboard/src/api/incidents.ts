// ============================================================
// sih-dashboard · src/api/incidents.ts
// Endpoints: GET /incidents, GET /incidents/{id}, PATCH /incidents/{id},
//            POST /incidents/{id}/dispatch, GET /incidents/{id}/recommendations
// ============================================================

import { apiClient, MOCK_MODE } from './client';
import {
  MOCK_INCIDENTS,
  MOCK_SOS_REPORTS,
} from '../mocks';
import type { Incident, DispatchRecord, DispatchRequest, SOSRequest } from '../types';

export interface IncidentListParams {
  bbox?: string;
  severity?: string;
  status?: string;
  since?: string;
}

export async function fetchIncidents(params?: IncidentListParams): Promise<Incident[]> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 400));
    return [...MOCK_INCIDENTS];
  }
  const res = await apiClient.get<Incident[]>('/incidents', { params });
  return res.data;
}

export async function fetchIncident(incidentId: string): Promise<{ incident: Incident; sos_reports: SOSRequest[] }> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 300));
    const inc = MOCK_INCIDENTS.find((i) => i.incident_id === incidentId);
    if (!inc) throw new Error(`Incident ${incidentId} not found`);
    return {
      incident: inc,
      sos_reports: MOCK_SOS_REPORTS[incidentId] ?? [],
    };
  }
  const res = await apiClient.get<any>(`/incidents/${incidentId}`);
  const data = res.data;

  // Defensive normalization: handle canonical wrapped shape or flat backend response shape
  if (data && typeof data === 'object') {
    if ('incident' in data && data.incident) {
      return {
        incident: data.incident as Incident,
        sos_reports: Array.isArray(data.sos_reports) ? data.sos_reports : [],
      };
    }
    // Flat response shape returned directly from backend
    const sos_reports = Array.isArray(data.sos_reports) ? data.sos_reports : [];
    const incident = { ...data };
    delete incident.sos_reports;
    return {
      incident: incident as Incident,
      sos_reports,
    };
  }

  throw new Error(`Invalid incident response format for ${incidentId}`);
}

export async function patchIncident(
  incidentId: string,
  update: Partial<Pick<Incident, 'status'>>
): Promise<Incident> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 300));
    const inc = MOCK_INCIDENTS.find((i) => i.incident_id === incidentId);
    if (!inc) throw new Error(`Incident ${incidentId} not found`);
    return { ...inc, ...update };
  }
  const res = await apiClient.patch<Incident>(`/incidents/${incidentId}`, update);
  return res.data;
}

export async function dispatchResource(
  incidentId: string,
  body: DispatchRequest
): Promise<DispatchRecord> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 500));
    return {
      dispatch_id: `disp-${Date.now()}`,
      incident_id: incidentId,
      resource_id: body.resource_id,
      quantity_dispatched: body.quantity,
      dispatched_by: 'usr-001',
      dispatched_at: new Date().toISOString(),
      eta_minutes: 12,
      status: 'dispatched',
    };
  }
  const res = await apiClient.post<DispatchRecord>(`/incidents/${incidentId}/dispatch`, body);
  return res.data;
}

export async function refreshSummary(incidentId: string): Promise<Incident> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 800));
    const inc = MOCK_INCIDENTS.find((i) => i.incident_id === incidentId);
    if (!inc) throw new Error(`Incident ${incidentId} not found`);
    return inc;
  }
  const res = await apiClient.post<Incident>(`/incidents/${incidentId}/refresh-summary`);
  return res.data;
}
