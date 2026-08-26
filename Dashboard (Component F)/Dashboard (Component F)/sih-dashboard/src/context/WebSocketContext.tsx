// ============================================================
// sih-dashboard · src/context/WebSocketContext.tsx
// Phase 3: WebSocket live updates.
// Connects to WS /ws/incidents?token=<access_token>
// Handles: disconnect, reconnect with exponential backoff, stale state.
// ============================================================

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { MOCK_MODE } from '../api/client';
import type { WsMessage } from '../types';

export type WsConnectionState = 'connecting' | 'connected' | 'disconnected' | 'error';

interface WebSocketContextValue {
  connectionState: WsConnectionState;
  lastMessage: WsMessage | null;
  reconnect: () => void;
  /** Increments on every *re*connect (not the first connect). Hooks watch this to resync missed events. */
  reconnectCount: number;
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

const WS_BASE_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8000/ws/incidents';
const MAX_BACKOFF_MS = 30_000;

export function WebSocketProvider({
  accessToken,
  children,
}: {
  accessToken: string | null;
  children: React.ReactNode;
}) {
  const [connectionState, setConnectionState] = useState<WsConnectionState>('disconnected');
  const [lastMessage, setLastMessage] = useState<WsMessage | null>(null);
  const [reconnectCount, setReconnectCount] = useState(0);
  const wsRef = useRef<WebSocket | null>(null);
  const backoffRef = useRef<number>(1000);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);
  // true after the first successful open; subsequent opens are reconnects
  const hasConnectedRef = useRef(false);

  const connect = useCallback(() => {
    const currentToken = sessionStorage.getItem('access_token');
    if (!currentToken || MOCK_MODE) return;
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setConnectionState('connecting');
    const url = `${WS_BASE_URL}?token=${encodeURIComponent(currentToken)}`;

    let ws: WebSocket;
    try {
      ws = new WebSocket(url);
    } catch {
      setConnectionState('error');
      return;
    }
    wsRef.current = ws;

    ws.onopen = () => {
      if (!mountedRef.current) return;
      backoffRef.current = 1000; // reset
      setConnectionState('connected');
      if (hasConnectedRef.current) {
        // This is a reconnect — signal hooks to resync REST state
        setReconnectCount((prev) => prev + 1);
      }
      hasConnectedRef.current = true;
    };

    ws.onmessage = (evt) => {
      if (!mountedRef.current) return;
      try {
        const msg: WsMessage = JSON.parse(evt.data as string);
        setLastMessage(msg);
      } catch {
        // ignore malformed messages
      }
    };

    ws.onerror = () => {
      if (!mountedRef.current) return;
      setConnectionState('error');
    };

    ws.onclose = () => {
      if (!mountedRef.current) return;
      setConnectionState('disconnected');
      wsRef.current = null;
      // Exponential backoff reconnect
      reconnectTimerRef.current = setTimeout(() => {
        if (mountedRef.current) {
          backoffRef.current = Math.min(backoffRef.current * 2, MAX_BACKOFF_MS);
          connect();
        }
      }, backoffRef.current);
    };
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    if (accessToken) {
      connect();
    }
    return () => {
      mountedRef.current = false;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      wsRef.current?.close();
    };
  }, [accessToken, connect]);

  const reconnect = useCallback(() => {
    if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
    backoffRef.current = 1000;
    wsRef.current?.close();
    wsRef.current = null;
    connect();
  }, [connect]);

  return (
    <WebSocketContext.Provider value={{ connectionState, lastMessage, reconnect, reconnectCount }}>
      {children}
    </WebSocketContext.Provider>
  );
}

export function useWebSocketCtx() {
  const ctx = useContext(WebSocketContext);
  if (!ctx) throw new Error('useWebSocketCtx must be used within WebSocketProvider');
  return ctx;
}
