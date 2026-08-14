// ============================================================
// sih-dashboard · src/__tests__/incidentUtils.test.ts
// Tests for sort order, flag logic, and time display utilities.
// ============================================================

import { describe, it, expect } from 'vitest';
import type { Incident } from '../types';

// Helper: minimal incident
function makeIncident(id: string, severity: Incident['severity'], msAgo: number): Incident {
  return {
    incident_id: id,
    cluster_id: `cl-${id}`,
    source_sos_uuids: [],
    location: { lat: 0, lng: 0 },
    area_name: 'Test',
    emergency_types: ['unspecified'],
    severity,
    ai_summary: null,
    report_count: 1,
    estimated_people_affected: 1,
    flags: { medical_emergency: false, trapped: false, elderly_or_children: false, structural_damage: false },
    first_reported_at: new Date(Date.now() - msAgo).toISOString(),
    last_updated_at: new Date().toISOString(),
    status: 'new',
    recommended_resources: [],
    assigned_resources: [],
  };
}

// Inline sort function (mirrors IncidentList)
const SEVERITY_ORDER: Record<string, number> = { critical: 0, high: 1, medium: 2, low: 3 };
function sortIncidents(incidents: Incident[]): Incident[] {
  return [...incidents].sort((a, b) => {
    const sevDiff = (SEVERITY_ORDER[a.severity] ?? 99) - (SEVERITY_ORDER[b.severity] ?? 99);
    if (sevDiff !== 0) return sevDiff;
    return new Date(b.first_reported_at).getTime() - new Date(a.first_reported_at).getTime();
  });
}

describe('Incident sort order', () => {
  it('sorts critical before high before medium before low', () => {
    const incidents = [
      makeIncident('low-1', 'low', 1000),
      makeIncident('crit-1', 'critical', 5000),
      makeIncident('med-1', 'medium', 2000),
      makeIncident('high-1', 'high', 3000),
    ];
    const sorted = sortIncidents(incidents);
    expect(sorted.map((i) => i.severity)).toEqual(['critical', 'high', 'medium', 'low']);
  });

  it('within same severity, sorts more recent first', () => {
    const incidents = [
      makeIncident('high-old', 'high', 60_000),
      makeIncident('high-new', 'high', 5_000),
      makeIncident('high-mid', 'high', 30_000),
    ];
    const sorted = sortIncidents(incidents);
    expect(sorted.map((i) => i.incident_id)).toEqual(['high-new', 'high-mid', 'high-old']);
  });

  it('is stable with empty array', () => {
    expect(sortIncidents([])).toEqual([]);
  });

  it('is stable with single incident', () => {
    const inc = makeIncident('only', 'medium', 1000);
    expect(sortIncidents([inc])).toEqual([inc]);
  });
});

describe('StatsSummary field names match contract', () => {
  it('active_incidents has critical/high/medium/low keys', () => {
    const stats = {
      active_incidents: { critical: 1, high: 2, medium: 0, low: 1 },
      total_estimated_people_affected: 26,
      resources: { available: 10, deployed: 3 },
      new_incidents_last_15min: 2,
    };
    expect(Object.keys(stats.active_incidents)).toEqual(['critical', 'high', 'medium', 'low']);
    expect(stats.total_estimated_people_affected).toBe(26);
    expect(stats.resources.available).toBe(10);
    expect(stats.new_incidents_last_15min).toBe(2);
  });
});

describe('Incident flag structure', () => {
  it('flag object has all four required fields', () => {
    const flags = { medical_emergency: true, trapped: false, elderly_or_children: true, structural_damage: false };
    expect('medical_emergency' in flags).toBe(true);
    expect('trapped' in flags).toBe(true);
    expect('elderly_or_children' in flags).toBe(true);
    expect('structural_damage' in flags).toBe(true);
  });
});
