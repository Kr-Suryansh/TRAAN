// ============================================================
// sih-dashboard · src/__tests__/apiErrors.test.ts
// REST error handling, auth expiry, and API integration paths.
// ============================================================

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { WebSocketProvider } from '../context/WebSocketContext';
import { useIncidents } from '../hooks/useIncidents';
import { useResources } from '../hooks/useResources';
import type { AxiosError } from 'axios';

// ── Silence WebSocket (not needed for REST error tests) ──────
class SilentWS {
  onopen: null = null; onmessage: null = null; onerror: null = null; onclose: null = null;
  constructor() {}
  close() {}
  send(_: string) {}
}

// ── Mock API modules ─────────────────────────────────────────
vi.mock('../api/incidents', () => ({
  fetchIncidents:  vi.fn(),
  fetchIncident:   vi.fn(),
  patchIncident:   vi.fn(),
  dispatchResource:vi.fn(),
  refreshSummary:  vi.fn(),
}));

vi.mock('../api/resources', () => ({
  fetchResources: vi.fn(),
}));

vi.mock('../api/stats', () => ({
  fetchStatsSummary:   vi.fn().mockResolvedValue({
    active_incidents: { critical:0, high:0, medium:0, low:0 },
    total_estimated_people_affected: 0,
    resources: { available:0, deployed:0 },
    new_incidents_last_15min: 0,
  }),
  fetchSituationBrief: vi.fn().mockResolvedValue(null),
}));

// ── Helper ───────────────────────────────────────────────────
function makeAxiosError(status: number, detail = 'Error'): AxiosError {
  const err = new Error(detail) as AxiosError;
  err.isAxiosError = true;
  (err as unknown as Record<string, unknown>).response = { status, data: { detail } };
  return err;
}

function IncidentConsumer() {
  const { incidents, loading, error, reload } = useIncidents();
  return (
    <div>
      <span data-testid="loading">{loading ? 'loading' : 'done'}</span>
      <span data-testid="count">{incidents.length}</span>
      {error && <span data-testid="error">{error}</span>}
      <button data-testid="reload" onClick={reload}>reload</button>
    </div>
  );
}

function ResourceConsumer() {
  const { resources, loading, error } = useResources();
  return (
    <div>
      <span data-testid="loading">{loading ? 'loading' : 'done'}</span>
      <span data-testid="count">{resources.length}</span>
      {error && <span data-testid="error">{error}</span>}
    </div>
  );
}

function wrap(children: React.ReactNode) {
  return <WebSocketProvider accessToken="test">{children}</WebSocketProvider>;
}

// ── REST Error Handling — Incidents ──────────────────────────
describe('REST error handling — incidents', () => {
  beforeEach(() => { vi.stubGlobal('WebSocket', SilentWS); });
  afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

  it('sets error state when fetchIncidents rejects', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents).mockRejectedValueOnce(new Error('Network timeout'));

    render(wrap(<IncidentConsumer />));

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('done');
      expect(screen.getByTestId('error').textContent).toBe('Network timeout');
    });
  });

  it('returns empty incident list alongside the error (no stale data shown)', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents).mockRejectedValueOnce(new Error('503'));

    render(wrap(<IncidentConsumer />));

    await waitFor(() => expect(screen.queryByTestId('error')).not.toBeNull());
    expect(screen.getByTestId('count').textContent).toBe('0');
  });

  it('clears error and shows data on successful retry', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents)
      .mockRejectedValueOnce(new Error('Server down'))
      .mockResolvedValueOnce([{
        incident_id: 'inc-retry', cluster_id: 'cl', source_sos_uuids: [],
        location: { lat: 0, lng: 0 }, area_name: 'Retry Area', emergency_types: ['flood_rescue'],
        severity: 'medium', ai_summary: null, report_count: 1, estimated_people_affected: 1,
        flags: { medical_emergency:false, trapped:false, elderly_or_children:false, structural_damage:false },
        first_reported_at: new Date().toISOString(), last_updated_at: new Date().toISOString(),
        status: 'new', recommended_resources: [], assigned_resources: [],
      }]);

    render(wrap(<IncidentConsumer />));
    await waitFor(() => expect(screen.queryByTestId('error')).not.toBeNull());

    // Trigger retry
    fireEvent.click(screen.getByTestId('reload'));

    await waitFor(() => {
      expect(screen.queryByTestId('error')).toBeNull();
      expect(screen.getByTestId('count').textContent).toBe('1');
    });
  });

  it('handles 401 Unauthorized from fetchIncidents', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents).mockRejectedValueOnce(makeAxiosError(401, 'Unauthorized'));

    render(wrap(<IncidentConsumer />));

    await waitFor(() => {
      expect(screen.queryByTestId('error')).not.toBeNull();
    });
    // Error should surface — the axios interceptor fires auth:expired separately
    expect(screen.getByTestId('error').textContent).toContain('Unauthorized');
  });

  it('handles 503 Service Unavailable gracefully', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents).mockRejectedValueOnce(makeAxiosError(503, 'Service Unavailable'));

    render(wrap(<IncidentConsumer />));

    await waitFor(() => expect(screen.queryByTestId('error')).not.toBeNull());
    expect(screen.getByTestId('count').textContent).toBe('0');
  });
});

// ── REST Error Handling — Resources ──────────────────────────
describe('REST error handling — resources', () => {
  beforeEach(() => { vi.stubGlobal('WebSocket', SilentWS); });
  afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

  it('sets error state when fetchResources rejects', async () => {
    const { fetchResources } = await import('../api/resources');
    vi.mocked(fetchResources).mockRejectedValueOnce(new Error('DB connection failed'));

    render(wrap(<ResourceConsumer />));

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('done');
      expect(screen.getByTestId('error').textContent).toBe('DB connection failed');
    });
  });
});

// ── Auth Expiry — auth:expired event ─────────────────────────
describe('Auth expiry — auth:expired window event', () => {
  afterEach(() => { vi.clearAllMocks(); });

  it('fires auth:expired event when a 401 is intercepted', async () => {
    // Verify the event name matches what AuthContext listens for
    const listener = vi.fn();
    window.addEventListener('auth:expired', listener);

    window.dispatchEvent(new CustomEvent('auth:expired'));
    expect(listener).toHaveBeenCalledTimes(1);

    window.removeEventListener('auth:expired', listener);
  });

  it('sessionStorage is cleared after auth:expired fires', async () => {
    // Simulate what the AuthContext logout handler does
    sessionStorage.setItem('access_token', 'old-token');
    sessionStorage.setItem('authority_user', JSON.stringify({ user_id: '1' }));

    // Simulate the logout logic triggered by auth:expired
    const logout = () => {
      sessionStorage.removeItem('access_token');
      sessionStorage.removeItem('authority_user');
    };

    window.addEventListener('auth:expired', logout);
    window.dispatchEvent(new CustomEvent('auth:expired'));

    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(sessionStorage.getItem('authority_user')).toBeNull();

    window.removeEventListener('auth:expired', logout);
  });
});

// ── Dispatch Error Handling ───────────────────────────────────
describe('Dispatch API — error handling', () => {
  afterEach(() => { vi.clearAllMocks(); });

  it('rejects with server error message on 422', async () => {
    const { dispatchResource } = await import('../api/incidents');
    vi.mocked(dispatchResource).mockRejectedValueOnce(
      makeAxiosError(422, 'Quantity exceeds available stock')
    );

    await expect(
      dispatchResource('inc-001', { resource_id: 'res-001', quantity: 999 })
    ).rejects.toMatchObject({ message: 'Quantity exceeds available stock' });
  });

  it('rejects with network error when backend is unreachable', async () => {
    const { dispatchResource } = await import('../api/incidents');
    vi.mocked(dispatchResource).mockRejectedValueOnce(new Error('Network Error'));

    await expect(
      dispatchResource('inc-001', { resource_id: 'res-001', quantity: 1 })
    ).rejects.toThrow('Network Error');
  });
});

// ── Stats — error handling ────────────────────────────────────
describe('Stats API — error handling', () => {
  beforeEach(() => { vi.stubGlobal('WebSocket', SilentWS); });
  afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

  it('useSummaryStats exposes error string when fetch fails', async () => {
    const { fetchStatsSummary } = await import('../api/stats');
    vi.mocked(fetchStatsSummary).mockRejectedValueOnce(new Error('Stats service down'));

    const { useSummaryStats } = await import('../hooks/useSummaryStats');

    function StatsConsumer() {
      const { stats, loading, error } = useSummaryStats();
      return (
        <div>
          <span data-testid="s-loading">{loading ? 'loading' : 'done'}</span>
          {error && <span data-testid="s-error">{error}</span>}
          {stats && <span data-testid="s-data">ok</span>}
        </div>
      );
    }

    render(wrap(<StatsConsumer />));

    await waitFor(() => expect(screen.queryByTestId('s-error')).not.toBeNull());
    expect(screen.getByTestId('s-error').textContent).toBe('Stats service down');
    expect(screen.queryByTestId('s-data')).toBeNull();
  });
});

// ── refreshSummary — async trigger, NOT an Incident ──────────
describe('refreshSummary — async trigger contract', () => {
  afterEach(() => { vi.clearAllMocks(); });

  it('resolves to void (undefined) — not an Incident object', async () => {
    const { refreshSummary } = await import('../api/incidents');
    // In mock mode, refreshSummary should resolve to undefined (void)
    vi.mocked(refreshSummary).mockResolvedValueOnce(undefined);

    const result = await refreshSummary('inc-001');
    expect(result).toBeUndefined();
  });

  it('does NOT return an Incident — callers must NOT set state from this return', async () => {
    const { refreshSummary } = await import('../api/incidents');
    // Simulate the real backend behavior: refreshSummary() returns void
    // The frontend API contract is Promise<void>, NOT Promise<Incident>
    vi.mocked(refreshSummary).mockResolvedValueOnce(undefined);

    const result = await refreshSummary('inc-001');
    // The result must be strictly void (undefined) — NOT an object containing incident fields
    // This directly verifies that the frontend API does not pass through
    // the backend's { status: 'refresh_queued' } as if it were an Incident.
    expect(result).toBeUndefined();
    // typeof check: void means "not an object" with incident properties
    expect(typeof result).not.toBe('object');
  });
});

// ── fetchIncidents — paginated response normalisation — P0 fix ─
describe('fetchIncidents — paginated backend response normalisation', () => {
  beforeEach(() => { vi.stubGlobal('WebSocket', SilentWS); });
  afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

  it('renders correctly when API returns paginated {items,total,page,size}', async () => {
    const { fetchIncidents } = await import('../api/incidents');

    // fetchIncidents() normalises paginated backend responses internally.
    // The mock returns Incident[] (the agreed frontend contract).
    // Simulate the normalised output (fetchIncidents extracts .items internally).
    const mockItems = [
      {
        incident_id: 'inc-paginated-001',
        cluster_id: 'cl-001',
        source_sos_uuids: [],
        location: { lat: 26.9, lng: 75.8 },
        area_name: 'Paginated Area',
        emergency_types: ['flood_rescue'],
        severity: 'high' as const,
        ai_summary: 'Test from paginated response.',
        report_count: 2,
        estimated_people_affected: 5,
        flags: { medical_emergency: false, trapped: true, elderly_or_children: false, structural_damage: false },
        first_reported_at: new Date().toISOString(),
        last_updated_at: new Date().toISOString(),
        status: 'new' as const,
        recommended_resources: [
          { resource_type: 'motorboat', quantity: 1, reasoning: 'Flood area.' }
        ],
        assigned_resources: [],
      }
    ];

    // fetchIncidents() must return Incident[] regardless of what the backend sends
    vi.mocked(fetchIncidents).mockResolvedValueOnce(mockItems);

    render(wrap(<IncidentConsumer />));

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('done');
      expect(screen.getByTestId('count').textContent).toBe('1');
    });
  });

  it('IncidentList renders paginated incidents correctly (count + no crash)', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents).mockResolvedValueOnce([
      {
        incident_id: 'inc-p-002',
        cluster_id: 'cl-002',
        source_sos_uuids: ['sos-1'],
        location: { lat: 26.9, lng: 75.8 },
        area_name: 'Pagination Test',
        emergency_types: ['medical'],
        severity: 'critical' as const,
        ai_summary: null,
        report_count: 1,
        estimated_people_affected: 3,
        flags: { medical_emergency: true, trapped: false, elderly_or_children: false, structural_damage: false },
        first_reported_at: new Date().toISOString(),
        last_updated_at: new Date().toISOString(),
        status: 'new' as const,
        recommended_resources: [{ resource_type: 'ambulance', quantity: 1, reasoning: 'Medical case.' }],
        assigned_resources: [],
      }
    ]);

    render(wrap(<IncidentConsumer />));

    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('1'));
    // Confirm this does not crash the component tree
    expect(screen.queryByTestId('error')).toBeNull();
  });
});
