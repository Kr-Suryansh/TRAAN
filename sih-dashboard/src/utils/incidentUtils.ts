// ============================================================
// sih-dashboard · src/utils/incidentUtils.ts
// Shared incident utility functions.
// Extracted here so both UI components and tests import
// the real production implementation — not a duplicate.
// ============================================================

import type { Incident } from '../types';

/**
 * Severity priority order for the incident list.
 * Lower number = higher urgency (displayed first).
 * Matches the canonical spec: critical > high > medium > low.
 */
export const SEVERITY_ORDER: Record<string, number> = {
  critical: 0,
  high: 1,
  medium: 2,
  low: 3,
};

/**
 * Sort incidents by severity (critical → low), then by recency
 * (most recently reported first within the same severity tier).
 *
 * This is the canonical sort used by IncidentList and tested in
 * incidentUtils.test.ts — do not duplicate this logic elsewhere.
 */
export function sortIncidents(incidents: Incident[]): Incident[] {
  return [...incidents].sort((a, b) => {
    const sevDiff =
      (SEVERITY_ORDER[a.severity] ?? 99) - (SEVERITY_ORDER[b.severity] ?? 99);
    if (sevDiff !== 0) return sevDiff;
    // Within the same severity: more recent first
    return (
      new Date(b.first_reported_at).getTime() -
      new Date(a.first_reported_at).getTime()
    );
  });
}
