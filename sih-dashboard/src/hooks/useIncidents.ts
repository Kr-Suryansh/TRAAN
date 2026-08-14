// ============================================================
// sih-dashboard · src/hooks/useIncidents.ts
// Merges REST (initial load) + WebSocket (live updates).
// On incident_created / incident_updated → update local state.
// ============================================================

import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchIncidents } from '../api/incidents';
import { useWebSocketCtx } from '../context/WebSocketContext';
import type { Incident, WsMessage } from '../types';

export function useIncidents() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastMessage, reconnectCount } = useWebSocketCtx();
  const loadedRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchIncidents();
      setIncidents(data);
    } catch (e) {
      setError((e as Error).message || 'Failed to load incidents');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!loadedRef.current) {
      loadedRef.current = true;
      load();
    }
  }, [load]);

  // Resync REST state after WS reconnects — recovers events missed during disconnection
  useEffect(() => {
    if (reconnectCount > 0) load();
  }, [reconnectCount, load]);

  // Handle WebSocket events — update state without page refresh
  useEffect(() => {
    if (!lastMessage) return;
    const msg = lastMessage as WsMessage;

    if (msg.event === 'incident_created') {
      const newInc = msg.data as Incident;
      setIncidents((prev) => {
        if (prev.some((i) => i.incident_id === newInc.incident_id)) return prev;
        return [newInc, ...prev];
      });
    } else if (msg.event === 'incident_updated') {
      const updated = msg.data as Incident;
      setIncidents((prev) =>
        prev.map((i) => (i.incident_id === updated.incident_id ? updated : i))
      );
    } else if (msg.event === 'incident_dispatched') {
      // Refresh stats after dispatch — signal via a re-fetch
      load();
    }
  }, [lastMessage, load]);

  return { incidents, loading, error, reload: load };
}
