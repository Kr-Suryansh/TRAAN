// ============================================================
// sih-dashboard · src/hooks/useSummaryStats.ts
// Fetches stats/summary; refreshes on relevant WS events.
// No polling — purely event-driven as per spec.
// ============================================================

import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchStatsSummary } from '../api/stats';
import { useWebSocketCtx } from '../context/WebSocketContext';
import type { StatsSummary, WsMessage } from '../types';

const REFRESH_EVENTS = new Set([
  'incident_created',
  'incident_updated',
  'incident_dispatched',
  'resource_updated',
]);

export function useSummaryStats() {
  const [stats, setStats] = useState<StatsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastMessage, reconnectCount } = useWebSocketCtx();
  const loadedRef = useRef(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await fetchStatsSummary();
      setStats(data);
    } catch (e) {
      setError((e as Error).message || 'Failed to load stats');
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

  // Resync after WS reconnects
  useEffect(() => {
    if (reconnectCount > 0) load();
  }, [reconnectCount, load]);

  // Refresh on relevant WS events — no timer
  useEffect(() => {
    if (!lastMessage) return;
    const msg = lastMessage as WsMessage;
    if (REFRESH_EVENTS.has(msg.event)) {
      load();
    }
  }, [lastMessage, load]);

  return { stats, loading, error };
}
