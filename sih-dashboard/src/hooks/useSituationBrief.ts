// ============================================================
// sih-dashboard · src/hooks/useSituationBrief.ts
// Fetches situation-brief; updates on situation_brief_updated.
// ============================================================

import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchSituationBrief } from '../api/stats';
import { useWebSocketCtx } from '../context/WebSocketContext';
import type { SituationBriefPayload, WsMessage } from '../types';

export function useSituationBrief() {
  const [brief, setBrief] = useState<SituationBriefPayload | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastMessage, reconnectCount } = useWebSocketCtx();
  const loadedRef = useRef(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await fetchSituationBrief();
      setBrief(data);
    } catch (e) {
      setError((e as Error).message || 'Failed to load situation brief');
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

  useEffect(() => {
    if (!lastMessage) return;
    const msg = lastMessage as WsMessage;
    if (msg.event === 'situation_brief_updated') {
      setBrief(msg.data as SituationBriefPayload);
    }
  }, [lastMessage]);

  return { brief, loading, error };
}
