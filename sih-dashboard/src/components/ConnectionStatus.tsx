// ============================================================
// sih-dashboard · src/components/ConnectionStatus.tsx
// Shows WebSocket connection state as a banner/dot.
// Phase 4: handles disconnect, error, reconnect button.
// ============================================================


import { useWebSocketCtx } from '../context/WebSocketContext';
import { MOCK_MODE } from '../api/client';

export function ConnectionStatus() {
  const { connectionState, reconnect } = useWebSocketCtx();

  if (MOCK_MODE) {
    return (
      <div className="ws-banner ws-connected" style={{ background: 'hsl(158 60% 10%)', borderLeft: '2px solid hsl(158 60% 42%)' }}>
        <span className="ws-dot" />
        <span style={{ color: 'hsl(158, 60%, 42%)' }}>Mock mode — live WS disabled</span>
      </div>
    );
  }

  const labels: Record<string, string> = {
    connected: 'Live — real-time updates active',
    connecting: 'Connecting to live feed…',
    disconnected: 'Disconnected — attempting to reconnect',
    error: 'Connection error',
  };

  const stateColors: Record<string, string> = {
    connected: 'hsl(158 60% 10%)',
    connecting: 'hsl(45 88% 10%)',
    disconnected: 'hsl(220 14% 15%)',
    error: 'hsl(24 90% 10%)',
  };

  return (
    <div
      className={`ws-banner ws-${connectionState}`}
      style={{ background: stateColors[connectionState] ?? 'transparent', borderLeft: '2px solid currentColor' }}
    >
      <span className="ws-dot" />
      <span style={{ color: connectionState === 'connected' ? 'hsl(158, 60%, 42%)' : 'hsl(220, 8%, 60%)' }}>
        {labels[connectionState]}
      </span>
      {(connectionState === 'disconnected' || connectionState === 'error') && (
        <button className="btn btn-ghost btn-sm" onClick={reconnect} style={{ marginLeft: 8 }}>
          Reconnect
        </button>
      )}
    </div>
  );
}
