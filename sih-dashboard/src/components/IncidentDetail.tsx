// ============================================================
// sih-dashboard · src/components/IncidentDetail.tsx
// Full incident record + contributing SOS reports +
// recommended_resources with reasoning.
// "Dispatch Resource" opens DispatchModal (human confirms there).
// ============================================================

import { useEffect, useState, type ReactNode } from 'react';
import {
  X, Send, RefreshCw, Users, FileText, MapPin,
  Clock, Heart, Home, AlertTriangle, ChevronDown, ChevronUp,
} from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import { fetchIncident, patchIncident, refreshSummary } from '../api/incidents';
import { DispatchModal } from './DispatchModal';
import type { Incident, Resource, SOSRequest, DispatchRecord } from '../types';

interface IncidentDetailProps {
  incidentId: string;
  resources: Resource[];
  onClose: () => void;
  onIncidentUpdated: (inc: Incident) => void;
  onDispatched: (record: DispatchRecord) => void;
  incidentProp?: Incident;
}

export function IncidentDetail({
  incidentId, resources, onClose, onIncidentUpdated, onDispatched, incidentProp
}: IncidentDetailProps) {
  const [incident, setIncident] = useState<Incident | null>(null);
  const [sosReports, setSosReports] = useState<SOSRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showDispatch, setShowDispatch] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [acknowledging, setAcknowledging] = useState(false);
  const [showSOS, setShowSOS] = useState(false);

  useEffect(() => {
    setLoading(true); setError(null);
    fetchIncident(incidentId)
      .then(({ incident: inc, sos_reports }) => {
        setIncident(inc);
        setSosReports(sos_reports);
      })
      .catch((e) => setError((e as Error).message || 'Failed to load incident'))
      .finally(() => setLoading(false));
  }, [incidentId]);

  useEffect(() => {
    if (incidentProp) {
      setIncident(incidentProp);
    }
  }, [incidentProp]);

  const handleAcknowledge = async () => {
    if (!incident || incident.status !== 'new') return;
    setAcknowledging(true);
    try {
      const updated = await patchIncident(incident.incident_id, { status: 'acknowledged' });
      setIncident(updated);
      onIncidentUpdated(updated);
    } catch { /* ignore */ } finally { setAcknowledging(false); }
  };

  const handleRefreshSummary = async () => {
    if (!incident) return;
    setRefreshing(true);
    try {
      // refreshSummary() returns void — it queues a backend AI refresh.
      // The updated incident will arrive via the incident_updated WebSocket event.
      // We keep the current incident in local state; do NOT replace it here.
      await refreshSummary(incident.incident_id);
      // Briefly show a "queued" indicator so the user knows the request was sent
      setTimeout(() => setRefreshing(false), 2000);
    } catch { setRefreshing(false); }
  };

  const handleDispatched = (record: DispatchRecord) => {
    setShowDispatch(false);
    onDispatched(record);
  };

  return (
    <>
      <div
        style={{
          display: 'flex', flexDirection: 'column', height: '100%',
          background: 'var(--surface-1)', borderLeft: '1px solid var(--border)',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', padding: '14px 16px', borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
          <div>
            <h2 style={{ fontSize: '0.95rem' }}>Incident Detail</h2>
            {incident && (
              <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
                <span className={`badge badge-${incident.severity}`}>{incident.severity}</span>
                <span className={`status-badge status-${incident.status}`}>{incident.status}</span>
              </div>
            )}
          </div>
          <button className="btn btn-ghost btn-sm" onClick={onClose} id="incident-detail-close">
            <X size={16} />
          </button>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '16px' }}>
          {loading && <div className="skeleton" style={{ height: 200, borderRadius: 'var(--radius-md)' }} />}
          {error && (
            <div className="empty-state" style={{ color: 'var(--high)' }}>
              <p style={{ fontWeight: 600 }}>Failed to load</p>
              <p style={{ fontSize: '0.78rem' }}>{error}</p>
            </div>
          )}

          {!loading && !error && incident && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* Location + area */}
              <Section title="Location">
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                  <MapPin size={14} color="var(--accent)" />
                  <span>{incident.area_name}</span>
                </div>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: 4 }}>
                  {incident.location.lat.toFixed(6)}, {incident.location.lng.toFixed(6)}
                </div>
              </Section>

              {/* AI Summary */}
              <Section
                title="AI Summary"
                action={
                  <button
                    className="btn btn-ghost btn-sm"
                    onClick={handleRefreshSummary}
                    disabled={refreshing}
                    id="refresh-summary-btn"
                  >
                    <RefreshCw size={12} style={{ animation: refreshing ? 'spin 1s linear infinite' : 'none' }} />
                    {refreshing ? 'Queued — awaiting update…' : 'Refresh'}
                  </button>
                }
              >
                <p style={{ fontSize: '0.85rem', color: incident.ai_summary ? 'var(--text-primary)' : 'var(--text-muted)', lineHeight: 1.6, fontStyle: incident.ai_summary ? 'normal' : 'italic' }}>
                  {incident.ai_summary ?? 'AI analysis pending — Gemini has not yet processed this incident.'}
                </p>
              </Section>

              {/* Stats */}
              <Section title="Overview">
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                  <Stat icon={<FileText size={13} />} label="Reports" value={String(incident.report_count)} />
                  <Stat icon={<Users size={13} />} label="Est. Affected" value={String(incident.estimated_people_affected)} />
                  <Stat icon={<Clock size={13} />} label="First reported" value={formatDistanceToNow(new Date(incident.first_reported_at), { addSuffix: true })} />
                  <Stat icon={<Clock size={13} />} label="Last updated" value={formatDistanceToNow(new Date(incident.last_updated_at), { addSuffix: true })} />
                </div>
                <div style={{ marginTop: 10 }}>
                  <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Emergency Types</p>
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                    {incident.emergency_types.map((t) => (
                      <span key={t} style={{ background: 'var(--surface-3)', border: '1px solid var(--border)', borderRadius: 4, padding: '2px 8px', fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'capitalize' }}>
                        {t.replace(/_/g, ' ')}
                      </span>
                    ))}
                  </div>
                </div>
                {/* Flags */}
                <div style={{ marginTop: 10, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {incident.flags.medical_emergency && <FlagPill icon={<Heart size={11} />} label="Medical Emergency" />}
                  {incident.flags.trapped && <FlagPill icon={<AlertTriangle size={11} />} label="People Trapped" />}
                  {incident.flags.elderly_or_children && <FlagPill icon={<Users size={11} />} label="Elderly / Children" />}
                  {incident.flags.structural_damage && <FlagPill icon={<Home size={11} />} label="Structural Damage" />}
                </div>
              </Section>

              {/* Recommendations — AI/OR-Tools guidance only, NOT dispatched resources */}
              <Section
                title="AI / OR-Tools Recommendations"
                action={
                  <span
                    style={{
                      fontSize: '0.65rem',
                      fontWeight: 700,
                      letterSpacing: '0.06em',
                      textTransform: 'uppercase',
                      color: 'hsl(43, 95%, 50%)',
                      background: 'hsl(43, 95%, 10%)',
                      border: '1px solid hsl(43, 95%, 25%)',
                      borderRadius: 4,
                      padding: '2px 7px',
                    }}
                  >
                    RECOMMENDED · Guidance Only
                  </span>
                }
              >
                {incident.recommended_resources.length === 0 ? (
                  <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
                    No recommendation yet — optimization may not have run for this incident.
                  </p>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {incident.recommended_resources.map((rec, i) => (
                      <div key={i} style={{ background: 'hsl(43, 90%, 6%)', border: '1px solid hsl(43, 90%, 18%)', borderRadius: 'var(--radius-sm)', padding: '10px 12px' }}>
                        <div style={{ display: 'flex', gap: 8, marginBottom: 4, alignItems: 'center' }}>
                          <span style={{ fontWeight: 700, fontSize: '0.85rem', textTransform: 'capitalize', color: 'var(--text-primary)' }}>
                            {rec.resource_type.replace(/_/g, ' ')}
                          </span>
                          <span style={{ background: 'var(--surface-3)', border: '1px solid var(--border)', borderRadius: 4, padding: '1px 7px', fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
                            × {rec.quantity}
                          </span>
                        </div>
                        {rec.agency && (
                          <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: 2 }}>
                            <strong>Source:</strong> {rec.agency}
                          </p>
                        )}
                        {rec.distance_km !== undefined && (
                          <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: 4 }}>
                            <strong>Distance:</strong> {rec.distance_km} km
                          </p>
                        )}
                        <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)', lineHeight: 1.5 }}>
                          <strong>Reason:</strong> {rec.reasoning}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </Section>

              {/* Assigned Resources — dispatched by authority, distinct from recommendations */}
              {incident.assigned_resources.length > 0 && (
                <Section
                  title="Assigned / Dispatched"
                  action={
                    <span
                      style={{
                        fontSize: '0.65rem',
                        fontWeight: 700,
                        letterSpacing: '0.06em',
                        textTransform: 'uppercase',
                        color: 'var(--low)',
                        background: 'hsl(142, 70%, 8%)',
                        border: '1px solid hsl(142, 70%, 22%)',
                        borderRadius: 4,
                        padding: '2px 7px',
                      }}
                    >
                      DISPATCHED
                    </span>
                  }
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    {incident.assigned_resources.map((rid) => (
                      <div key={rid} style={{ fontFamily: 'var(--font-mono)', fontSize: '0.78rem', color: 'var(--low)', background: 'hsl(142, 70%, 5%)', border: '1px solid hsl(142, 70%, 18%)', borderRadius: 'var(--radius-sm)', padding: '6px 10px' }}>
                        {rid}
                      </div>
                    ))}
                  </div>
                </Section>
              )}

              {/* SOS Reports (collapsible) */}
              <Section
                title={`Contributing SOS Reports (${sosReports.length})`}
                action={
                  <button className="btn btn-ghost btn-sm" onClick={() => setShowSOS(!showSOS)}>
                    {showSOS ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                  </button>
                }
              >
                {showSOS && (
                  sosReports.length === 0 ? (
                    <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>No raw SOS reports linked yet.</p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {sosReports.map((sos) => (
                        <div key={sos.uuid} style={{ background: 'var(--surface-3)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: '10px 12px', fontSize: '0.78rem' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.7rem', color: 'var(--text-muted)' }}>{sos.uuid.slice(0, 16)}…</span>
                            <span style={{ color: 'var(--text-muted)', fontSize: '0.7rem' }}>
                              {(() => { try { return format(new Date(sos.created_at), 'HH:mm, dd MMM'); } catch { return '—'; } })()}
                            </span>
                          </div>
                          {sos.custom_message && (
                            <p style={{ color: 'var(--text-secondary)', marginBottom: 4, lineHeight: 1.5 }}>
                              "{sos.custom_message}"
                            </p>
                          )}
                          <div style={{ display: 'flex', gap: 10, color: 'var(--text-muted)', fontSize: '0.72rem' }}>
                            <span style={{ textTransform: 'capitalize' }}>{sos.emergency_type.replace(/_/g, ' ')}</span>
                            {sos.people_count != null && <span>· {sos.people_count} people</span>}
                            <span>· {sos.relay_hop_count} hop{sos.relay_hop_count !== 1 ? 's' : ''}</span>
                            {sos.is_quick_sos && <span style={{ color: 'var(--medium)' }}>· Quick SOS</span>}
                          </div>
                          {sos.medical_snapshot?.medical_conditions?.length ? (
                            <div style={{ marginTop: 4, color: 'var(--text-muted)', fontSize: '0.7rem' }}>
                              Conditions: {sos.medical_snapshot.medical_conditions.join(', ')}
                            </div>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  )
                )}
                {!showSOS && sosReports.length > 0 && (
                  <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                    Click ↑ to view {sosReports.length} raw report{sosReports.length !== 1 ? 's' : ''}.
                  </p>
                )}
              </Section>
            </div>
          )}
        </div>

        {/* Footer actions */}
        {incident && (
          <div style={{ padding: '12px 16px', borderTop: '1px solid var(--border)', display: 'flex', gap: 10, flexShrink: 0 }}>
            {incident.status === 'new' && (
              <button className="btn btn-ghost" onClick={handleAcknowledge} disabled={acknowledging} id="acknowledge-btn">
                {acknowledging ? 'Acknowledging…' : 'Acknowledge'}
              </button>
            )}
            <button
              id="open-dispatch-btn"
              className="btn btn-danger"
              onClick={() => setShowDispatch(true)}
              disabled={incident.status === 'resolved'}
              style={{ marginLeft: 'auto' }}
            >
              <Send size={14} />
              Dispatch Resource…
            </button>
          </div>
        )}
      </div>

      {showDispatch && incident && (
        <DispatchModal
          incident={incident}
          resources={resources}
          onClose={() => setShowDispatch(false)}
          onDispatched={handleDispatched}
        />
      )}

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </>
  );
}

function Section({
  title, children, action,
}: { title: string; children: ReactNode; action?: ReactNode }) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase' }}>
          {title}
        </p>
        {action}
      </div>
      {children}
    </div>
  );
}

function Stat({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div style={{ background: 'var(--surface-2)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: '8px 10px' }}>
      <div style={{ display: 'flex', gap: 5, color: 'var(--text-muted)', fontSize: '0.7rem', marginBottom: 3 }}>
        {icon} {label}
      </div>
      <div style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text-primary)' }}>{value}</div>
    </div>
  );
}

function FlagPill({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'hsl(213, 90%, 12%)', color: 'var(--accent)', border: '1px solid hsl(213, 90%, 28%)', borderRadius: 4, padding: '2px 8px', fontSize: '0.73rem', fontWeight: 600 }}>
      {icon} {label}
    </span>
  );
}
