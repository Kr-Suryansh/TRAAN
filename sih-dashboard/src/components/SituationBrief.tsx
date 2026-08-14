// ============================================================
// sih-dashboard · src/components/SituationBrief.tsx
// Displays AI-generated situation text from GET /situation-brief.
// Updates on situation_brief_updated WS event.
// ============================================================


import { Newspaper, Clock } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { useSituationBrief } from '../hooks/useSituationBrief';

export function SituationBrief() {
  const { brief, loading, error } = useSituationBrief();

  return (
    <div
      style={{
        display: 'flex', flexDirection: 'column',
        background: 'var(--surface-1)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-lg)',
        overflow: 'hidden',
      }}
    >
      <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8 }}>
        <Newspaper size={13} color="var(--text-muted)" />
        <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', flex: 1 }}>
          Situation Brief
        </span>
        {brief && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: 'var(--text-muted)', fontSize: '0.68rem' }}>
            <Clock size={10} />
            {(() => { try { return formatDistanceToNow(new Date(brief.updated_at), { addSuffix: true }); } catch { return '—'; } })()}
          </div>
        )}
      </div>

      <div style={{ padding: '12px 14px', flex: 1, overflowY: 'auto', maxHeight: 180 }}>
        {loading && (
          <>
            <div className="skeleton" style={{ height: 14, width: '100%', borderRadius: 4, marginBottom: 8 }} />
            <div className="skeleton" style={{ height: 14, width: '85%', borderRadius: 4, marginBottom: 8 }} />
            <div className="skeleton" style={{ height: 14, width: '70%', borderRadius: 4 }} />
          </>
        )}

        {error && (
          <p style={{ fontSize: '0.8rem', color: 'var(--high)', fontStyle: 'italic' }}>
            Failed to load situation brief.
          </p>
        )}

        {!loading && !error && !brief && (
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
            No situation brief available yet. Gemini generates one every 5–10 minutes.
          </p>
        )}

        {!loading && !error && brief && (
          <p
            style={{
              fontSize: '0.8rem',
              color: 'var(--text-secondary)',
              lineHeight: 1.7,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}
          >
            {brief.text}
          </p>
        )}
      </div>
    </div>
  );
}
