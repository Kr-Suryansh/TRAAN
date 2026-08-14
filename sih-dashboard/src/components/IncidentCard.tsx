// ============================================================
// sih-dashboard · src/components/IncidentCard.tsx
// Compact card for the incident list.
// Shows: ai_summary, severity badge, report_count,
//        estimated_people_affected, flags, area_name,
//        time elapsed since first_reported_at, status.
// NO photo thumbnails — system is text-only.
// ============================================================

import type { ReactNode } from 'react';
import { Heart, Users, Home, AlertTriangle, Clock, FileText } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import type { Incident } from '../types';

interface IncidentCardProps {
  incident: Incident;
  isSelected: boolean;
  onClick: () => void;
}

const SEVERITY_LEFT_BORDER: Record<string, string> = {
  critical: 'var(--critical)',
  high:     'var(--high)',
  medium:   'var(--medium)',
  low:      'var(--low)',
};

export function IncidentCard({ incident: inc, isSelected, onClick }: IncidentCardProps) {
  const elapsed = (() => {
    try {
      return formatDistanceToNow(new Date(inc.first_reported_at), { addSuffix: true });
    } catch {
      return '—';
    }
  })();

  return (
    <div
      id={`incident-card-${inc.incident_id}`}
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => e.key === 'Enter' && onClick()}
      style={{
        background: isSelected ? 'var(--surface-3)' : 'var(--surface-2)',
        border: `1px solid ${isSelected ? 'var(--accent)' : 'var(--border)'}`,
        borderLeft: `3px solid ${SEVERITY_LEFT_BORDER[inc.severity] ?? 'var(--border)'}`,
        borderRadius: 'var(--radius-md)',
        padding: '12px 14px',
        cursor: 'pointer',
        transition: 'background var(--duration-fast) var(--ease), border-color var(--duration-fast) var(--ease)',
        outline: 'none',
      }}
      onMouseEnter={(e) => {
        if (!isSelected) (e.currentTarget as HTMLElement).style.background = 'var(--surface-3)';
      }}
      onMouseLeave={(e) => {
        if (!isSelected) (e.currentTarget as HTMLElement).style.background = 'var(--surface-2)';
      }}
    >
      {/* Header row */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          <span className={`badge badge-${inc.severity}`}>{inc.severity}</span>
          <span className={`status-badge status-${inc.status}`}>{inc.status}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--text-muted)', flexShrink: 0, fontSize: '0.72rem' }}>
          <Clock size={11} />
          {elapsed}
        </div>
      </div>

      {/* Area + summary */}
      <div style={{ marginBottom: 8 }}>
        <div style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: '0.85rem', marginBottom: 3 }}>
          {inc.area_name}
        </div>
        <div
          style={{
            fontSize: '0.8rem',
            color: inc.ai_summary ? 'var(--text-secondary)' : 'var(--text-muted)',
            lineHeight: 1.5,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }}
        >
          {inc.ai_summary ?? (
            <span style={{ fontStyle: 'italic' }}>AI analysis pending…</span>
          )}
        </div>
      </div>

      {/* Metrics row */}
      <div style={{ display: 'flex', gap: 14, marginBottom: 8, color: 'var(--text-muted)', fontSize: '0.75rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <FileText size={12} />
          <span>{inc.report_count} report{inc.report_count !== 1 ? 's' : ''}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <Users size={12} />
          <span>{inc.estimated_people_affected} affected</span>
        </div>
      </div>

      {/* Flags */}
      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
        <FlagChip active={inc.flags.medical_emergency} icon={<Heart size={10} />} label="Medical" />
        <FlagChip active={inc.flags.trapped} icon={<AlertTriangle size={10} />} label="Trapped" />
        <FlagChip active={inc.flags.elderly_or_children} icon={<Users size={10} />} label="Elderly/Child" />
        <FlagChip active={inc.flags.structural_damage} icon={<Home size={10} />} label="Structural" />
      </div>
    </div>
  );
}

function FlagChip({ active, icon, label }: { active: boolean; icon: ReactNode; label: string }) {
  return (
    <span className={`flag-icon ${active ? 'active' : ''}`}>
      {icon} {label}
    </span>
  );
}
