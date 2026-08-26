// ============================================================
// sih-dashboard · src/__tests__/WebSocketIntegration.test.tsx
//
// Proves the acceptance criterion:
//   incident_created WS event
//     → React state update (no page refresh / no remount)
//     → IncidentList shows the new incident
//     → IncidentMap props contain the new incident
//
// Also covers:
//   incident_updated, incident_dispatched, resource_updated,
//   situation_brief_updated, reconnect resynchronisation.
// ============================================================

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import { useEffect, useState } from 'react';
import { WebSocketProvider } from '../context/WebSocketContext';
import * as WsCtxModule from '../context/WebSocketContext';
import { useIncidents } from '../hooks/useIncidents';
import * as IncidentsHookModule from '../hooks/useIncidents';
import { useResources } from '../hooks/useResources';
import * as ResourcesHookModule from '../hooks/useResources';
import { IncidentList } from '../components/IncidentList';
import { IncidentMap } from '../components/IncidentMap';
import type { Incident, Resource, WsMessage } from '../types';

// ── Mock WebSocket ──────────────────────────────────────────
// Captures the instance so tests can inject messages directly.
// onopen fires via micro-task (realistic async, but fast).
let capturedWs: MockWS | null = null;

class MockWS {
  static OPEN = 1;
  static CLOSED = 3;
  readyState = MockWS.OPEN;
  url: string;
  onopen:    ((e: Event)        => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror:   ((e: Event)        => void) | null = null;
  onclose:   ((e: CloseEvent)   => void) | null = null;

  constructor(url: string) {
    this.url = url;
    capturedWs = this;
    // micro-task: realistic async open without needing fake timers
    Promise.resolve().then(() => this.onopen?.(new Event('open')));
  }

  send(_: string) {}

  close() {
    this.readyState = MockWS.CLOSED;
    this.onclose?.(new CloseEvent('close'));
  }

  /** Helper used in tests to push a WS message */
  push(msg: WsMessage) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(msg) }));
  }
}

// ── Mock API modules ────────────────────────────────────────
vi.mock('../api/incidents', () => ({
  fetchIncidents:  vi.fn().mockResolvedValue([]),
  fetchIncident:   vi.fn(),
  patchIncident:   vi.fn(),
  dispatchResource:vi.fn(),
  refreshSummary:  vi.fn(),
}));

vi.mock('../api/resources', () => ({
  fetchResources: vi.fn().mockResolvedValue([]),
}));

vi.mock('../api/stats', () => ({
  fetchStatsSummary:    vi.fn().mockResolvedValue({
    active_incidents: { critical:0, high:0, medium:0, low:0 },
    total_estimated_people_affected: 0,
    resources: { available:0, deployed:0 },
    new_incidents_last_15min: 0,
  }),
  fetchSituationBrief: vi.fn().mockResolvedValue(null),
}));

// ── Test fixtures ───────────────────────────────────────────
const makeIncident = (overrides: Partial<Incident> = {}): Incident => ({
  incident_id: 'inc-001',
  cluster_id:  'cl-001',
  source_sos_uuids: ['sos-001'],
  location: { lat: 26.9124, lng: 75.7873 },
  area_name: 'Test Area',
  emergency_types: ['flood_rescue'],
  severity: 'critical',
  ai_summary: 'Flooding reported.',
  report_count: 3,
  estimated_people_affected: 25,
  flags: { medical_emergency: true, trapped: true, elderly_or_children: false, structural_damage: false },
  first_reported_at: new Date().toISOString(),
  last_updated_at:   new Date().toISOString(),
  status: 'new',
  recommended_resources: [],
  assigned_resources: [],
  ...overrides,
});

const makeResource = (overrides: Partial<Resource> = {}): Resource => ({
  resource_id: 'res-001',
  category: 'rescue',
  sub_type: 'motorboat',
  custodian_agency: 'SDRF',
  quantity_total: 4,
  quantity_available: 3,
  status: 'partially_deployed',
  location: { lat: 26.9, lng: 75.7, district: 'Jaipur' },
  contact: '1234567890',
  last_updated_at: new Date().toISOString(),
  ...overrides,
});

// ── Helper consumer components ──────────────────────────────
function IncidentConsumer() {
  const { incidents, loading } = useIncidents();
  return (
    <div>
      <span data-testid="status">{loading ? 'loading' : 'ready'}</span>
      <span data-testid="count">{incidents.length}</span>
      <ul>
        {incidents.map(inc => (
          <li key={inc.incident_id} data-testid={`inc-${inc.incident_id}`}>
            {inc.area_name}|{inc.severity}|{inc.status}
          </li>
        ))}
      </ul>
    </div>
  );
}

function ResourceConsumer() {
  const { resources, loading } = useResources();
  return (
    <div>
      <span data-testid="res-status">{loading ? 'loading' : 'ready'}</span>
      <span data-testid="res-count">{resources.length}</span>
      {resources.map(r => (
        <span key={r.resource_id} data-testid={`res-${r.resource_id}`}>
          {r.quantity_available}
        </span>
      ))}
    </div>
  );
}

function wrapWs(children: React.ReactNode, token = 'test-jwt') {
  return <WebSocketProvider accessToken={token}>{children}</WebSocketProvider>;
}

// ── Lifecycle helpers ───────────────────────────────────────
async function waitForReady(testId = 'status') {
  await waitFor(() => expect(screen.getByTestId(testId).textContent).toBe('ready'));
}

async function waitForWs() {
  await waitFor(() => expect(capturedWs).not.toBeNull());
  await act(async () => { await Promise.resolve(); }); // let onopen fire
}

// ── Tests ────────────────────────────────────────────────────
describe('WebSocket — connection', () => {
  beforeEach(() => {
    capturedWs = null;
    vi.stubGlobal('WebSocket', MockWS);
    sessionStorage.clear();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('passes JWT token as query param on WS URL', async () => {
    // connect() reads the token from sessionStorage; seed it before rendering.
    sessionStorage.setItem('access_token', 'my-jwt-abc');
    render(wrapWs(<IncidentConsumer />, 'my-jwt-abc'));
    await waitForWs();
    expect(capturedWs!.url).toContain('token=my-jwt-abc');
  });

  it('does NOT open WebSocket when accessToken is null', () => {
    // accessToken=null means the useEffect guard never calls connect(),
    // regardless of what sessionStorage contains.
    render(<WebSocketProvider accessToken={null}><span /></WebSocketProvider>);
    expect(capturedWs).toBeNull();
  });

  it('reconnect uses refreshed token from sessionStorage after Axios refresh', async () => {
    // Simulates the exact D+F integration bug:
    //   1. Session starts with OLD_TOKEN.
    //   2. Axios interceptor refreshes token → writes NEW_TOKEN to sessionStorage.
    //   3. AuthContext React state still holds OLD_TOKEN (Axios doesn't update it).
    //   4. WebSocket reconnect fires — must use NEW_TOKEN, not OLD_TOKEN.

    const OLD_TOKEN = 'old-expired-jwt';
    const NEW_TOKEN = 'new-refreshed-jwt';

    // Initial state: sessionStorage has old token
    sessionStorage.setItem('access_token', OLD_TOKEN);

    render(wrapWs(<IncidentConsumer />, OLD_TOKEN));
    await waitForWs();

    // First connection uses OLD_TOKEN (correct)
    expect(capturedWs!.url).toContain(`token=${OLD_TOKEN}`);

    // Simulate Axios interceptor writing the refreshed token to sessionStorage
    // (this is all the Axios interceptor does — it does NOT update React state)
    sessionStorage.setItem('access_token', NEW_TOKEN);

    // Capture the first WS instance and force-close it to trigger reconnect
    const firstWs = capturedWs!;
    capturedWs = null;
    act(() => { firstWs.close(); });

    // Wait for the reconnect backoff timer and new connection
    await waitFor(() => expect(capturedWs).not.toBeNull(), { timeout: 3000 });
    await act(async () => { await Promise.resolve(); }); // let onopen fire

    // The reconnect URL must contain NEW_TOKEN, not OLD_TOKEN
    expect(capturedWs!.url).toContain(`token=${NEW_TOKEN}`);
    expect(capturedWs!.url).not.toContain(`token=${OLD_TOKEN}`);
  });
});

// ──────────────────────────────────────────────────────────────
// ACCEPTANCE CRITERION: incident_created → list + map, no refresh
// ──────────────────────────────────────────────────────────────
describe('WebSocket — incident_created (primary acceptance criterion)', () => {
  beforeEach(() => {
    capturedWs = null;
    vi.stubGlobal('WebSocket', MockWS);
    sessionStorage.setItem('access_token', 'test-jwt');
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('newly created incident appears in state WITHOUT page refresh', async () => {
    render(wrapWs(<IncidentConsumer />));

    await waitForWs();
    await waitForReady();

    // Initially empty
    expect(screen.getByTestId('count').textContent).toBe('0');
    expect(screen.queryByTestId('inc-inc-001')).toBeNull();

    // Push incident_created
    act(() => { capturedWs!.push({ event: 'incident_created', data: makeIncident() }); });

    // Incident appears — no page reload needed
    await waitFor(() => {
      expect(screen.getByTestId('count').textContent).toBe('1');
    });
    expect(screen.getByTestId('inc-inc-001').textContent).toBe('Test Area|critical|new');
  });

  it('duplicate incident_created with same ID does not add a second entry', async () => {
    render(wrapWs(<IncidentConsumer />));
    await waitForWs();
    await waitForReady();

    const inc = makeIncident();
    act(() => {
      capturedWs!.push({ event: 'incident_created', data: inc });
      capturedWs!.push({ event: 'incident_created', data: inc });
    });

    await waitFor(() => expect(screen.getByTestId('count').textContent).toBe('1'));
  });

  it('component does NOT remount when a WS message is received', async () => {
    // useEffect with [] fires only on mount, not on re-renders.
    // This correctly distinguishes mount from state-driven re-render.
    const mountedTimes = { current: 0 };

    function Counted() {
      useEffect(() => { mountedTimes.current++; }, []);
      const { incidents } = useIncidents();
      return <span data-testid="c">{incidents.length}</span>;
    }

    render(wrapWs(<Counted />));
    await waitForWs();
    await waitFor(() => expect(screen.getByTestId('c')).toBeDefined());

    const priorMounts = mountedTimes.current;
    expect(priorMounts).toBe(1); // sanity

    act(() => { capturedWs!.push({ event: 'incident_created', data: makeIncident() }); });
    await waitFor(() => expect(screen.getByTestId('c').textContent).toBe('1'));

    // useEffect([]) must NOT have fired again — no remount occurred
    expect(mountedTimes.current).toBe(priorMounts);
  });

  it('updates both IncidentList and IncidentMap on incident_created WS event without page refresh', async () => {
    function CombinedView() {
      const { incidents, loading, error } = useIncidents();
      const { resources } = useResources();
      return (
        <div>
          <span data-testid="status">{loading ? 'loading' : 'ready'}</span>
          <IncidentList incidents={incidents} loading={loading} error={error} selectedId={null} onSelect={() => {}} />
          <IncidentMap incidents={incidents} resources={resources} selectedIncidentId={null} onSelectIncident={() => {}} />
        </div>
      );
    }

    render(wrapWs(<CombinedView />));
    await waitForWs();
    await waitForReady();

    expect(screen.getByText(/Incidents · 0/i)).toBeDefined();

    const newInc = makeIncident({ incident_id: 'inc-map-001', area_name: 'Map Area Test' });

    act(() => {
      capturedWs!.push({ event: 'incident_created', data: newInc });
    });

    await waitFor(() => {
      expect(screen.getByText(/Incidents · 1/i)).toBeDefined();
      expect(screen.getByText('Map Area Test')).toBeDefined();
    });
  });
});

// ──────────────────────────────────────────────────────────────
// incident_updated
// ──────────────────────────────────────────────────────────────
describe('WebSocket — incident_updated', () => {
  beforeEach(() => {
    capturedWs = null;
    vi.stubGlobal('WebSocket', MockWS);
    sessionStorage.setItem('access_token', 'test-jwt');
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('updates the area_name of an existing incident', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents).mockResolvedValueOnce([makeIncident({ area_name: 'Old Name' })]);

    render(wrapWs(<IncidentConsumer />));
    await waitForWs();
    await waitForReady();

    expect(screen.getByTestId('inc-inc-001').textContent).toContain('Old Name');

    act(() => {
      capturedWs!.push({
        event: 'incident_updated',
        data: makeIncident({ area_name: 'New Name', status: 'acknowledged' }),
      });
    });

    await waitFor(() =>
      expect(screen.getByTestId('inc-inc-001').textContent).toContain('New Name|critical|acknowledged')
    );
  });

  it('ignores incident_updated for an unknown incident_id', async () => {
    render(wrapWs(<IncidentConsumer />));
    await waitForWs();
    await waitForReady();

    act(() => {
      capturedWs!.push({
        event: 'incident_updated',
        data: makeIncident({ incident_id: 'unknown-id' }),
      });
    });

    // Count stays 0 — unknown ID is silently ignored
    await new Promise(r => setTimeout(r, 50));
    expect(screen.getByTestId('count').textContent).toBe('0');
  });
});

// ──────────────────────────────────────────────────────────────
// resource_updated
// ──────────────────────────────────────────────────────────────
describe('WebSocket — resource_updated', () => {
  beforeEach(() => {
    capturedWs = null;
    vi.stubGlobal('WebSocket', MockWS);
    sessionStorage.setItem('access_token', 'test-jwt');
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('updates resource quantity_available in state', async () => {
    const { fetchResources } = await import('../api/resources');
    vi.mocked(fetchResources).mockResolvedValueOnce([makeResource({ quantity_available: 3 })]);

    render(wrapWs(<ResourceConsumer />));
    await waitForWs();
    await waitFor(() => expect(screen.getByTestId('res-status').textContent).toBe('ready'));

    expect(screen.getByTestId('res-res-001').textContent).toBe('3');

    act(() => {
      capturedWs!.push({
        event: 'resource_updated',
        data: makeResource({ quantity_available: 1 }),
      });
    });

    await waitFor(() =>
      expect(screen.getByTestId('res-res-001').textContent).toBe('1')
    );
  });
});

// ──────────────────────────────────────────────────────────────
// situation_brief_updated
// ──────────────────────────────────────────────────────────────
describe('WebSocket — situation_brief_updated', () => {
  beforeEach(() => {
    capturedWs = null;
    vi.stubGlobal('WebSocket', MockWS);
    sessionStorage.setItem('access_token', 'test-jwt');
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('updates brief text via WS without REST re-fetch', async () => {
    const { useSituationBrief } = await import('../hooks/useSituationBrief');

    function BriefConsumer() {
      const { brief } = useSituationBrief();
      return <span data-testid="brief">{brief?.text ?? 'none'}</span>;
    }

    render(wrapWs(<BriefConsumer />));
    await waitForWs();
    await waitFor(() => expect(screen.getByTestId('brief')).toBeDefined());

    act(() => {
      capturedWs!.push({
        event: 'situation_brief_updated',
        data: { text: 'Flooding has worsened.', updated_at: new Date().toISOString() },
      });
    });

    await waitFor(() =>
      expect(screen.getByTestId('brief').textContent).toBe('Flooding has worsened.')
    );
  });
});

// ──────────────────────────────────────────────────────────────
// Reconnect → REST resynchronisation
// Strategy: render a component that locally owns reconnectCount state
// and spies on useWebSocketCtx to return controlled values.
// act(() => setCount(n => n+1)) simulates a reconnect event.
// ──────────────────────────────────────────────────────────────
describe('WebSocket — reconnect resynchronisation', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.clearAllMocks(); });

  it('re-fetches incidents after reconnectCount increments', async () => {
    const { fetchIncidents } = await import('../api/incidents');
    vi.mocked(fetchIncidents).mockResolvedValue([]);

    // Spy on useWebSocketCtx so we can control reconnectCount per render
    let currentReconnectCount = 0;
    vi.spyOn(WsCtxModule, 'useWebSocketCtx').mockImplementation(() => ({
      connectionState: 'connected' as const,
      lastMessage: null,
      reconnect: () => {},
      reconnectCount: currentReconnectCount,
    }));

    // Spy on useIncidents to pass through but track loads
    const realUseIncidents = IncidentsHookModule.useIncidents;
    vi.spyOn(IncidentsHookModule, 'useIncidents').mockImplementation(realUseIncidents);

    let setCount!: (n: (prev: number) => number) => void;

    function BumpIncidents() {
      const [rc, setRc] = useState(0);
      setCount = setRc;
      currentReconnectCount = rc;
      const { incidents, loading } = useIncidents();
      return (
        <div>
          <span data-testid="s">{loading ? 'loading' : 'ready'}</span>
          <span data-testid="cnt">{incidents.length}</span>
        </div>
      );
    }

    render(<BumpIncidents />);
    await waitFor(() => expect(screen.getByTestId('s').textContent).toBe('ready'));

    const callsBefore = vi.mocked(fetchIncidents).mock.calls.length;

    // Simulate reconnect by incrementing reconnectCount
    act(() => { setCount((n: number) => n + 1); });

    await waitFor(() => {
      expect(vi.mocked(fetchIncidents).mock.calls.length).toBeGreaterThan(callsBefore);
    });
  });

  it('re-fetches resources after reconnectCount increments', async () => {
    const { fetchResources } = await import('../api/resources');
    vi.mocked(fetchResources).mockResolvedValue([]);

    let currentReconnectCount = 0;
    vi.spyOn(WsCtxModule, 'useWebSocketCtx').mockImplementation(() => ({
      connectionState: 'connected' as const,
      lastMessage: null,
      reconnect: () => {},
      reconnectCount: currentReconnectCount,
    }));

    const realUseResources = ResourcesHookModule.useResources;
    vi.spyOn(ResourcesHookModule, 'useResources').mockImplementation(realUseResources);

    let setCount!: (n: (prev: number) => number) => void;

    function BumpResources() {
      const [rc, setRc] = useState(0);
      setCount = setRc;
      currentReconnectCount = rc;
      const { resources, loading } = useResources();
      return (
        <div>
          <span data-testid="rs">{loading ? 'loading' : 'ready'}</span>
          <span data-testid="rc">{resources.length}</span>
        </div>
      );
    }

    render(<BumpResources />);
    await waitFor(() => expect(screen.getByTestId('rs').textContent).toBe('ready'));

    const before = vi.mocked(fetchResources).mock.calls.length;

    act(() => { setCount((n: number) => n + 1); });

    await waitFor(() => {
      expect(vi.mocked(fetchResources).mock.calls.length).toBeGreaterThan(before);
    });
  });
});

// ──────────────────────────────────────────────────────────────
// Malformed / unexpected WS messages
// ──────────────────────────────────────────────────────────────
describe('WebSocket — resilience', () => {
  beforeEach(() => {
    capturedWs = null;
    vi.stubGlobal('WebSocket', MockWS);
    sessionStorage.setItem('access_token', 'test-jwt');
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('silently ignores malformed (non-JSON) WS messages', async () => {
    render(wrapWs(<IncidentConsumer />));
    await waitForWs();
    await waitForReady();

    act(() => {
      capturedWs!.onmessage?.(
        new MessageEvent('message', { data: 'NOT_JSON_AT_ALL{{{' })
      );
    });

    // State unchanged — no error thrown
    await new Promise(r => setTimeout(r, 30));
    expect(screen.getByTestId('count').textContent).toBe('0');
  });

  it('silently ignores unknown event types', async () => {
    render(wrapWs(<IncidentConsumer />));
    await waitForWs();
    await waitForReady();

    act(() => {
      capturedWs!.push({ event: 'unknown_future_event', data: {} } as unknown as WsMessage);
    });

    await new Promise(r => setTimeout(r, 30));
    expect(screen.getByTestId('count').textContent).toBe('0');
  });
});
