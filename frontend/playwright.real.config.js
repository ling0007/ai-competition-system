import { defineConfig, devices } from '@playwright/test'

if (!/^ai_competition_phase5_e2e_[a-f0-9]{8}$/.test(process.env.PHASE5_E2E_DB_NAME ?? '')) {
  throw new Error('Real E2E requires explicit PHASE5_E2E_DB_NAME=ai_competition_phase5_e2e_<8 hex>')
}
if (!process.env.PHASE5_E2E_PASSWORD) {
  throw new Error('Real E2E requires PHASE5_E2E_PASSWORD for isolated fixture accounts')
}

export default defineConfig({
  testDir: './e2e',
  testMatch: 'real-backend.spec.js',
  timeout: 180_000,
  workers: 1,
  use: { ...devices['Desktop Chrome'], baseURL: 'http://127.0.0.1:4176', actionTimeout: 15_000, trace: 'retain-on-failure' },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4176 --strictPort',
    url: 'http://127.0.0.1:4176/login',
    reuseExistingServer: false,
    env: { VITE_USE_MOCK: 'false', VITE_API_BASE_URL: 'http://127.0.0.1:8081' },
  },
})
