// ============================================================
// sih-dashboard · src/pages/DashboardPage.tsx
// Main dashboard layout — all 7 required panels assembled.
// Layout: top summary strip, left map+brief, center incident list,
//         right detail pane, bottom resource panel.
// ============================================================

import { useState, useCallback } from 'react';
import { LogOut, Activity } from 'lucide-react';
import { SummaryStrip } from '../components/SummaryStrip';
import { IncidentMap } from '../components/IncidentMap';
import { IncidentList } from '../components/IncidentList';
import { IncidentDetail } from '../components/IncidentDetail';
import { ResourcePanel } from '../components/ResourcePanel';
import { SituationBrief } from '../components/SituationBrief';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { useIncidents } from '../hooks/useIncidents';
import { useResources } from '../hooks/useResources';
import { useAuth } from '../context/AuthContext';
import type { Incident, DispatchRecord } from '../types';

export function DashboardPage() {
  const { user, logout } = useAuth();
  const { incidents, loading: incLoading, error: incError, reload: reloadIncidents } = useIncidents();
  const { resources, loading: resLoading, error: resError } = useResources();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const handleSelectIncident = useCallback((id: string) => {
    setSelectedId((prev) => (prev === id ? null : id));
  }, []);

  const handleIncidentUpdated = useCallback((updated: Incident) => {
    void updated;
    reloadIncidents();
  }, [reloadIncidents]);

  const handleDispatched = useCallback((record: DispatchRecord) => {
    void record;
    reloadIncidents();
  }, [reloadIncidents]);

  return (
    <div
      style={{
        display: 'flex', flexDirection: 'column', height: '100%',
        background: 'var(--surface-0)', overflow: 'hidden',
      }}
    >
      {/* ── Top nav bar ─────────────────────────────────────────── */}
      <header
        style={{
          display: 'flex', alignItems: 'center', gap: 12,
          padding: '0 16px', height: 44,
          background: 'var(--surface-1)',
          borderBottom: '1px solid var(--border)',
          flexShrink: 0,
          zIndex: 100,
        }}
      >
        <Activity size={16} color="var(--accent)" />
        <span style={{ fontWeight: 700, fontSize: '0.88rem', letterSpacing: '-0.01em' }}>
          SIH Disaster Response
        </span>
        <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', padding: '1px 7px', background: 'var(--surface-3)', border: '1px solid var(--border)', borderRadius: 4 }}>
          Authority Dashboard
        </span>

        <div style={{ flex: 1 }} />

        <ConnectionStatus />

        {user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-primary)' }}>{user.name}</div>
              <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>{user.role} · {user.agency.split(',')[0]}</div>
            </div>
            <button id="logout-btn" className="btn btn-ghost btn-sm" onClick={logout} aria-label="Log out">
              <LogOut size={14} />
            </button>
          </div>
        )}
      </header>

      {/* ── Summary strip ───────────────────────────────────────── */}
      <SummaryStrip />

      {/* ── Main content ────────────────────────────────────────── */}
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: selectedId ? '1fr 320px 300px' : '1fr 320px', gridTemplateRows: '1fr 220px', overflow: 'hidden', minHeight: 0 }}>

        {/* MAP + SITUATION BRIEF (left, top+bottom) */}
        <div style={{ gridRow: '1 / 3', display: 'flex', flexDirection: 'column', minHeight: 0, overflow: 'hidden', padding: '10px 0 10px 12px', gap: 10 }}>
          <div style={{ flex: 1, minHeight: 0 }}>
            <IncidentMap
              incidents={incidents}
              resources={resources}
              selectedIncidentId={selectedId}
              onSelectIncident={handleSelectIncident}
            />
          </div>
          <SituationBrief />
        </div>

        {/* INCIDENT LIST (center) */}
        <div style={{ gridRow: '1 / 3', padding: '0 0 10px 12px', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          <IncidentList
            incidents={incidents}
            loading={incLoading}
            error={incError}
            selectedId={selectedId}
            onSelect={handleSelectIncident}
          />
        </div>

        {/* INCIDENT DETAIL (right) — only when selected */}
        {selectedId && (
          <div style={{ gridRow: '1 / 3', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <IncidentDetail
              key={selectedId}
              incidentId={selectedId}
              incidentProp={incidents.find((i) => i.incident_id === selectedId)}
              resources={resources}
              onClose={() => setSelectedId(null)}
              onIncidentUpdated={handleIncidentUpdated}
              onDispatched={handleDispatched}
            />
          </div>
        )}

        {/* RESOURCE PANEL (bottom, spans map + list columns) — resources owned here */}
        {!selectedId && (
          <div style={{ gridColumn: '1 / 3', gridRow: 2, padding: '0 12px 12px 12px', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <ResourcePanel resources={resources} loading={resLoading} error={resError} />
          </div>
        )}
      </div>
    </div>
  );
}
