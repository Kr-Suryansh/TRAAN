// ============================================================
// sih-dashboard · src/api/incidents.ts
// Endpoints: GET /incidents, GET /incidents/{id}, PATCH /incidents/{id},
//            POST /incidents/{id}/dispatch, POST /incidents/{id}/refresh-summary
//
// Frontend API contract: all functions return the canonical types defined in
// types/index.ts. Adapter logic is isolated here — it must never leak into
// UI components. The public surface of this module is the agreed frontend
// contract, not the current backend implementation.
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

/**
 * Internal adapter type for the paginated shape that the current backend
 * may return. This type is intentionally NOT exported — it is an
 * implementation detail of fetchIncidents() only and must not leak into
 * UI components. The public return type remains Incident[].
 */
interface PaginatedIncidentsResponse {
  items: Incident[];
  total: number;
  page: number;
  size: number;
}

/**
 * Fetch the incident list. Returns Incident[] per the agreed frontend contract.
 *
 * Adapter note: the current backend may return a paginated envelope
 * { items, total, page, size }. This is normalised internally so callers
 * always receive Incident[]. This adapter does NOT change the agreed contract.
 */
export async function fetchIncidents(params?: IncidentListParams): Promise<Incident[]> {
  if (MOCK_MODE) {
    await new Promise((r) => setTimeout(r, 400));
    return [...MOCK_INCIDENTS];
  }
  const res = await apiClient.get<Incident[] | PaginatedIncidentsResponse>('/incidents', { params });
  const data = res.data;
  // Normalise paginated backend response to Incident[]
  if (Array.isArray(data)) return data;
  if (data && typeof data === 'object' && 'items' in data && Array.isArray(data.items)) {
    return data.items;
  }
  // Fallback: return empty array rather than crashing
  return [];
}

/**
 * Fetch a single incident with its contributing SOS reports.
 *
 * Adapter note: the canonical response shape is { incident, sos_reports }.
 * The current backend may also return a flat object, or use the field
 * `source_sos_reports` instead of `sos_reports`. Both are normalised here.
 * The canonical frontend field is always `sos_reports`.
 */
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
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const res = await apiClient.get<any>(`/incidents/${incidentId}`);
  const data = res.data;

  if (data && typeof data === 'object') {
    // Canonical wrapped shape: { incident, sos_reports }
    if ('incident' in data && data.incident) {
      return {
        incident: data.incident as Incident,
        // Normalise source_sos_reports → sos_reports (backend field variance)
        sos_reports: Array.isArray(data.sos_reports)
          ? data.sos_reports
          : Array.isArray(data.source_sos_reports)
            ? data.source_sos_reports
            : [],
      };
    }
    // Flat response: the incident fields are at the top level
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const rawData = data as Record<string, any>;
    const sos_reports: SOSRequest[] = Array.isArray(rawData.sos_reports)
      ? rawData.sos_reports
      : Array.isArray(rawData.source_sos_reports)
        ? rawData.source_sos_reports
        : [];
    const incident = { ...rawData } as unknown as Incident;
    // Remove SOS fields from the incident object to keep types clean
    delete (incident as unknown as Record<string, unknown>).sos_reports;
    delete (incident as unknown as Record<string, unknown>).source_sos_reports;
    return { incident, sos_reports };
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

/**
 * Trigger an asynchronous Gemini AI summary refresh for an incident.
 *
 * This is an async trigger — it queues a refresh on the backend.
 * It does NOT return an updated Incident. The updated incident will arrive
 * via the `incident_updated` WebSocket event.
 *
 * Do NOT treat the response of this call as an Incident — it is not.
 * The backend currently returns { status: "refresh_queued" }.
 * Keep the current incident in local state; update only when the WS event arrives.
 */
export async function refreshSummary(incidentId: string): Promise<void> {
  if (MOCK_MODE) {
    // Simulate network round-trip; state will be updated by a simulated WS event
    // in a real integration. In mock mode, we simply resolve — the UI keeps the
    // current incident and waits for the incident_updated WS event.
    await new Promise((r) => setTimeout(r, 800));
    return;
  }
  // Fire-and-forget: backend queues the refresh and returns { status: "refresh_queued" }
  // We deliberately discard the response — it is not a valid Incident object.
  await apiClient.post(`/incidents/${incidentId}/refresh-summary`);
}
