// ============================================================
// sih-dashboard · src/components/SummaryStrip.tsx
// Top-of-dashboard stats: severity counts, people affected,
// resources available/deployed, new in last 15 min.
// Refreshes on WS events, NOT on a timer.
// ============================================================

import React from 'react';
import { AlertTriangle, Users, Package, Clock, TrendingUp } from 'lucide-react';
import { useSummaryStats } from '../hooks/useSummaryStats';

export function SummaryStrip() {
  const { stats, loading } = useSummaryStats();

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
        padding: '12px 16px',
        background: 'var(--surface-1)',
        borderBottom: '1px solid var(--border)',
      }}
    >
      {/* Severity counts */}
      <StatCard
        icon={<AlertTriangle size={15} />}
        label="Active Incidents"
        loading={loading}
        content={
          stats ? (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
              {stats.active_incidents.critical > 0 && (
                <span className="badge badge-critical">{stats.active_incidents.critical} CRIT</span>
              )}
              {stats.active_incidents.high > 0 && (
                <span className="badge badge-high">{stats.active_incidents.high} HIGH</span>
              )}
              {stats.active_incidents.medium > 0 && (
                <span className="badge badge-medium">{stats.active_incidents.medium} MED</span>
              )}
              {stats.active_incidents.low > 0 && (
                <span className="badge badge-low">{stats.active_incidents.low} LOW</span>
              )}
              {stats.active_incidents.critical + stats.active_incidents.high +
               stats.active_incidents.medium + stats.active_incidents.low === 0 && (
                <span style={{ color: 'var(--text-muted)' }}>None</span>
              )}
            </div>
          ) : null
        }
      />

      {/* People affected */}
      <StatCard
        icon={<Users size={15} />}
        label="Est. People Affected"
        loading={loading}
        content={
          stats ? (
            <span style={{ fontSize: '1.4rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              {stats.total_estimated_people_affected.toLocaleString('en-IN')}
            </span>
          ) : null
        }
      />

      {/* Resources */}
      <StatCard
        icon={<Package size={15} />}
        label="Resources"
        loading={loading}
        content={
          stats ? (
            <div style={{ display: 'flex', gap: 12 }}>
              <div>
                <div style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--low)' }}>
                  {stats.resources.available}
                </div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Available</div>
              </div>
              <div style={{ width: 1, background: 'var(--border)' }} />
              <div>
                <div style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--status-dispatched)' }}>
                  {stats.resources.deployed}
                </div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Deployed</div>
              </div>
            </div>
          ) : null
        }
      />

      {/* New in last 15 min */}
      <StatCard
        icon={<Clock size={15} />}
        label="New (last 15 min)"
        loading={loading}
        content={
          stats ? (
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
              <span
                style={{
                  fontSize: '1.4rem',
                  fontWeight: 700,
                  color: stats.new_incidents_last_15min > 0 ? 'var(--high)' : 'var(--text-muted)',
                }}
              >
                {stats.new_incidents_last_15min}
              </span>
              {stats.new_incidents_last_15min > 0 && <TrendingUp size={14} color="var(--high)" />}
            </div>
          ) : null
        }
      />
    </div>
  );
}

function StatCard({
  icon,
  label,
  content,
  loading,
}: {
  icon: React.ReactNode;
  label: string;
  content: React.ReactNode;
  loading: boolean;
}) {
  return (
    <div
      style={{
        background: 'var(--surface-2)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-md)',
        padding: '10px 14px',
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        minHeight: 72,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-muted)' }}>
        {icon}
        <span style={{ fontSize: '0.72rem', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
          {label}
        </span>
      </div>
      {loading ? (
        <div className="skeleton" style={{ height: 24, width: '60%' }} />
      ) : (
        content
      )}
    </div>
  );
}
