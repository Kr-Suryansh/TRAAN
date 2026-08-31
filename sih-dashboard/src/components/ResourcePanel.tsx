// ============================================================
// sih-dashboard · src/components/ResourcePanel.tsx
// Resources grouped by category. Live-updated on resource_updated WS event.
// Clearly labelled as mock IDRN-style registry.
//
// State ownership: resources/loading/error are owned by DashboardPage via
// useResources() and passed here as props. ResourcePanel does NOT call
// useResources() — that would create a second independent state instance
// and cause duplicate REST requests.
// ============================================================

import { useState } from 'react';
import { Package, ChevronDown, ChevronUp, Phone } from 'lucide-react';
import type { Resource, ResourceCategory } from '../types';

const CATEGORY_LABELS: Record<ResourceCategory, string> = {
  medical:       '🏥 Medical',
  rescue:        '🚤 Rescue',
  shelter:       '🏕 Shelter',
  transport:     '🚗 Transport',
  communication: '📡 Communication',
};

const STATUS_DOT: Record<string, string> = {
  available:         'var(--low)',
  partially_deployed:'var(--medium)',
  deployed:          'var(--high)',
  maintenance:       'var(--gray-500)',
};

interface ResourcePanelProps {
  resources: Resource[];
  loading: boolean;
  error: string | null;
}

export function ResourcePanel({ resources, loading, error }: ResourcePanelProps) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set(['medical', 'rescue']));

  const grouped = resources.reduce<Record<string, Resource[]>>((acc, r) => {
    acc[r.category] = acc[r.category] ?? [];
    acc[r.category].push(r);
    return acc;
  }, {});

  const toggle = (cat: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(cat) ? next.delete(cat) : next.add(cat);
      return next;
    });
  };

  return (
    <div
      style={{
        display: 'flex', flexDirection: 'column', height: '100%',
        background: 'var(--surface-1)', borderTop: '1px solid var(--border)',
        overflowY: 'auto',
      }}
    >
      <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <Package size={14} color="var(--text-muted)" />
        <h2 style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase' }}>
          Resource Registry
        </h2>
        <span style={{ marginLeft: 'auto', fontSize: '0.65rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
          Mock IDRN-style
        </span>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}>
        {loading && (
          <div style={{ padding: '0 14px' }}>
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 36, borderRadius: 'var(--radius-sm)', marginBottom: 8 }} />
            ))}
          </div>
        )}

        {error && (
          <div className="empty-state" style={{ color: 'var(--high)' }}>
            <p style={{ fontSize: '0.8rem' }}>Failed to load resources</p>
          </div>
        )}

        {!loading && !error && Object.keys(CATEGORY_LABELS).map((cat) => {
          const items = grouped[cat] ?? [];
          const isOpen = expanded.has(cat);

          return (
            <div key={cat} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
              <button
                onClick={() => toggle(cat)}
                style={{
                  width: '100%', display: 'flex', alignItems: 'center', gap: 8,
                  padding: '8px 14px', background: 'transparent', border: 'none',
                  cursor: 'pointer', color: 'var(--text-secondary)', textAlign: 'left',
                }}
              >
                <span style={{ fontSize: '0.8rem', fontWeight: 600, flex: 1 }}>
                  {CATEGORY_LABELS[cat as ResourceCategory] ?? cat}
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>{items.length}</span>
                {isOpen ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
              </button>

              {isOpen && items.length === 0 && (
                <p style={{ padding: '4px 14px 10px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>None in registry.</p>
              )}

              {isOpen && items.map((res) => (
                <div key={res.resource_id} style={{ padding: '6px 14px 8px 22px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                      <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span
                          style={{
                            width: 7, height: 7, borderRadius: '50%', flexShrink: 0,
                            background: STATUS_DOT[res.status] ?? 'var(--gray-500)',
                          }}
                        />
                        {res.sub_type.replace(/_/g, ' ')}
                      </div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: 2 }}>
                        {res.custodian_agency}
                      </div>
                      <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: 1 }}>
                        {res.location.district}
                      </div>
                    </div>
                    <div style={{ textAlign: 'right', flexShrink: 0 }}>
                      <div style={{ fontSize: '0.85rem', fontWeight: 700, color: res.quantity_available > 0 ? 'var(--low)' : 'var(--gray-500)' }}>
                        {res.quantity_available}
                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontWeight: 400 }}>
                          /{res.quantity_total}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.67rem', color: 'var(--text-muted)', marginTop: 1, textTransform: 'capitalize' }}>
                        {res.status.replace(/_/g, ' ')}
                      </div>
                    </div>
                  </div>
                  {res.contact && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 4, color: 'var(--text-muted)', fontSize: '0.7rem' }}>
                      <Phone size={10} />
                      <a href={`tel:${res.contact}`} style={{ color: 'inherit', textDecoration: 'none' }}>{res.contact}</a>
                    </div>
                  )}
                </div>
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
