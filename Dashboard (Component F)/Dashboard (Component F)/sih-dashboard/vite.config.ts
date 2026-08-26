import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    open: false,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    // Set VITE_MOCK_MODE=false so WebSocketContext creates real WebSocket instances.
    // Without this, MOCK_MODE evaluates to true (undefined !== 'false') and the
    // WebSocket integration tests fail because capturedWs never gets populated.
    env: {
      VITE_MOCK_MODE: 'false',
    },
  },
})
