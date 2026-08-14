// ============================================================
// sih-dashboard · src/hooks/useResources.ts
// Fetches resources; updates on resource_updated WS events.
// ============================================================

import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchResources } from '../api/resources';
import { useWebSocketCtx } from '../context/WebSocketContext';
import type { Resource, WsMessage } from '../types';

export function useResources() {
  const [resources, setResources] = useState<Resource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { lastMessage, reconnectCount } = useWebSocketCtx();
  const loadedRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchResources();
      setResources(data);
    } catch (e) {
      setError((e as Error).message || 'Failed to load resources');
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
    if (msg.event === 'resource_updated') {
      const updated = msg.data as Resource;
      setResources((prev) =>
        prev.map((r) => (r.resource_id === updated.resource_id ? updated : r))
      );
    }
  }, [lastMessage]);

  return { resources, loading, error, reload: load };
}
