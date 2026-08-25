// ============================================================
// sih-dashboard · src/components/DispatchModal.tsx
// CRITICAL: Human-in-the-loop — dispatch ONLY on explicit confirm click.
// No auto-submit path exists in this component.
// Calls POST /incidents/{id}/dispatch on explicit button press only.
// ============================================================

import { useState } from 'react';
import { X, Send, AlertTriangle } from 'lucide-react';
import { dispatchResource } from '../api/incidents';
import type { Incident, Resource, DispatchRecord } from '../types';

interface DispatchModalProps {
  incident: Incident;
  resources: Resource[];
  onClose: () => void;
  onDispatched: (record: DispatchRecord) => void;
}

export function DispatchModal({ incident, resources, onClose, onDispatched }: DispatchModalProps) {
  // Pre-fill from first recommended resource if available
  const firstRec = incident.recommended_resources[0];
  const availableResources = resources.filter(
    (r) => r.quantity_available > 0 && r.status !== 'deployed' && r.status !== 'maintenance'
  );

  const [selectedResourceId, setSelectedResourceId] = useState<string>(
    availableResources[0]?.resource_id ?? ''
  );
  const [quantity, setQuantity] = useState<number>(firstRec?.quantity ?? 1);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);

  const selectedResource = resources.find((r) => r.resource_id === selectedResourceId);

  const handleConfirm = async () => {
    if (!selectedResourceId) { setError('Select a resource.'); return; }
    if (!quantity || quantity < 1) { setError('Quantity must be at least 1.'); return; }
    if (selectedResource && quantity > selectedResource.quantity_available) {
      setError(`Only ${selectedResource.quantity_available} units available.`);
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const record = await dispatchResource(incident.incident_id, {
        resource_id: selectedResourceId,
        quantity,
      });
      onDispatched(record);
    } catch (e) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setError((e as any)?.response?.data?.detail ?? (e as Error).message ?? 'Dispatch failed.');
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Dispatch Resource">
      <div className="modal" style={{ padding: 0, overflow: 'hidden' }}>
        {/* Header */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '16px 20px',
            borderBottom: '1px solid var(--border)',
            background: 'var(--surface-2)',
          }}
        >
          <div>
            <h2 style={{ fontSize: '0.95rem' }}>Dispatch Resource</h2>
            <p style={{ fontSize: '0.76rem', color: 'var(--text-muted)', marginTop: 2 }}>
              {incident.area_name}
            </p>
          </div>
          <button
            id="dispatch-modal-close"
            className="btn btn-ghost btn-sm"
            onClick={onClose}
            aria-label="Close dispatch modal"
          >
            <X size={16} />
          </button>
        </div>

        <div style={{ padding: '20px' }}>
          {/* Recommended resources (read-only guidance — NOT dispatched) */}
          {incident.recommended_resources.length > 0 && (
            <div style={{ marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase' }}>
                  AI / OR-Tools Recommendations
                </p>
                <span
                  style={{
                    fontSize: '0.62rem', fontWeight: 700, letterSpacing: '0.06em',
                    textTransform: 'uppercase',
                    color: 'hsl(43, 95%, 50%)',
                    background: 'hsl(43, 95%, 10%)',
                    border: '1px solid hsl(43, 95%, 25%)',
                    borderRadius: 4, padding: '2px 7px',
                  }}
                  aria-label="These are AI-generated recommendations only, not confirmed dispatches"
                >
                  RECOMMENDED · Guidance Only
                </span>
              </div>
              {incident.recommended_resources.map((rec, i) => (
                <div
                  key={i}
                  style={{
                    background: 'hsl(43, 90%, 5%)',
                    border: '1px solid hsl(43, 90%, 16%)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '8px 12px',
                    marginBottom: 6,
                    fontSize: '0.8rem',
                  }}
                >
                  <div style={{ display: 'flex', gap: 8, marginBottom: 3 }}>
                    <span style={{ fontWeight: 600, textTransform: 'capitalize' }}>{rec.resource_type}</span>
                    <span style={{ color: 'var(--text-muted)' }}>× {rec.quantity}</span>
                  </div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>{rec.reasoning}</div>
                </div>
              ))}
            </div>
          )}

          {/* Resource selector */}
          <div style={{ marginBottom: 16 }}>
            <label
              htmlFor="dispatch-resource-select"
              style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}
            >
              Resource to dispatch
            </label>
            {availableResources.length === 0 ? (
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', color: 'var(--high)', padding: '10px 0' }}>
                <AlertTriangle size={14} />
                <span style={{ fontSize: '0.82rem' }}>No resources currently available.</span>
              </div>
            ) : (
              <select
                id="dispatch-resource-select"
                className="input"
                value={selectedResourceId}
                onChange={(e) => {
                  setSelectedResourceId(e.target.value);
                  setError(null);
                  setConfirmed(false);
                }}
              >
                {availableResources.map((r) => (
                  <option key={r.resource_id} value={r.resource_id}>
                    {r.sub_type} — {r.custodian_agency} ({r.quantity_available} avail.)
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Quantity */}
          <div style={{ marginBottom: 16 }}>
            <label
              htmlFor="dispatch-quantity"
              style={{ display: 'block', fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}
            >
              Quantity
            </label>
            <input
              id="dispatch-quantity"
              type="number"
              className="input"
              min={1}
              max={selectedResource?.quantity_available ?? 999}
              value={quantity}
              onChange={(e) => {
                setQuantity(parseInt(e.target.value, 10) || 1);
                setError(null);
                setConfirmed(false);
              }}
            />
            {selectedResource && (
              <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: 4 }}>
                Max available: {selectedResource.quantity_available}
              </p>
            )}
          </div>

          {/* Confirmation checkbox — explicit human step */}
          <div
            style={{
              background: 'hsl(0, 82%, 7%)',
              border: '1px solid hsl(0, 82%, 25%)',
              borderRadius: 'var(--radius-md)',
              padding: '12px 14px',
              marginBottom: 20,
            }}
          >
            <label
              htmlFor="dispatch-confirm-checkbox"
              style={{ display: 'flex', alignItems: 'flex-start', gap: 10, cursor: 'pointer' }}
            >
              <input
                id="dispatch-confirm-checkbox"
                type="checkbox"
                checked={confirmed}
                onChange={(e) => setConfirmed(e.target.checked)}
                style={{ marginTop: 2, accentColor: 'var(--danger)', flexShrink: 0 }}
              />
              <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                I, the authority, confirm this dispatch is necessary and appropriate.
                This action will create a dispatch record and cannot be automatically reversed.
              </span>
            </label>
          </div>

          {/* Error */}
          {error && (
            <div
              style={{
                display: 'flex',
                gap: 8,
                alignItems: 'center',
                color: 'var(--high)',
                fontSize: '0.8rem',
                marginBottom: 12,
                padding: '8px 12px',
                background: 'var(--high-bg)',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--high-border)',
              }}
            >
              <AlertTriangle size={14} />
              {error}
            </div>
          )}

          {/* Action buttons */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <button id="dispatch-cancel-btn" className="btn btn-ghost" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            {/* HUMAN CONFIRMATION REQUIRED — this is the only path to dispatch */}
            <button
              id="dispatch-confirm-btn"
              className="btn btn-danger"
              onClick={handleConfirm}
              disabled={
                !confirmed ||
                !selectedResourceId ||
                availableResources.length === 0 ||
                submitting
              }
            >
              {submitting ? (
                <span>Dispatching…</span>
              ) : (
                <>
                  <Send size={14} />
                  Confirm Dispatch
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
