// ============================================================
// sih-dashboard · src/components/IncidentList.tsx
// Sorted by severity (critical→low), then by first_reported_at desc.
// Sort logic lives in src/utils/incidentUtils.ts (single source of truth).
// ============================================================

import { IncidentCard } from './IncidentCard';
import { sortIncidents } from '../utils/incidentUtils';
import type { Incident } from '../types';

interface IncidentListProps {
  incidents: Incident[];
  loading: boolean;
  error: string | null;
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function IncidentList({ incidents, loading, error, selectedId, onSelect }: IncidentListProps) {
  const sorted = sortIncidents(incidents);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        overflowY: 'auto',
        height: '100%',
        padding: '12px 12px 12px 0',
      }}
    >
      {/* Section header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingRight: 4 }}>
        <h2 style={{ fontSize: '0.82rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
          Incidents · {incidents.length}
        </h2>
      </div>

      {loading && Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="skeleton" style={{ height: 110, borderRadius: 'var(--radius-md)' }} />
      ))}

      {!loading && error && (
        <div className="empty-state" style={{ color: 'var(--high)' }}>
          <AlertIcon />
          <p style={{ fontWeight: 600 }}>Failed to load incidents</p>
          <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{error}</p>
        </div>
      )}

      {!loading && !error && sorted.length === 0 && (
        <div className="empty-state">
          <InboxIcon />
          <p style={{ fontWeight: 600 }}>No active incidents</p>
          <p style={{ fontSize: '0.78rem' }}>All clear in the monitored area.</p>
        </div>
      )}

      {!loading && !error && sorted.map((inc) => (
        <IncidentCard
          key={inc.incident_id}
          incident={inc}
          isSelected={inc.incident_id === selectedId}
          onClick={() => onSelect(inc.incident_id)}
        />
      ))}
    </div>
  );
}

function AlertIcon() {
  return <svg width={32} height={32} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}><path d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/></svg>;
}
function InboxIcon() {
  return <svg width={32} height={32} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5}><path d="M22 12h-6l-2 3H10l-2-3H2"/><path d="M5.45 5.11L2 12v6a2 2 0 002 2h16a2 2 0 002-2v-6l-3.45-6.89A2 2 0 0016.76 4H7.24a2 2 0 00-1.79 1.11z"/></svg>;
}
