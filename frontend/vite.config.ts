import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'
import fs from 'node:fs'

export default defineConfig(({ mode }) => {
  const rootEnvDir = path.resolve(import.meta.dirname, '..')
  const envDir = fs.existsSync(path.resolve(rootEnvDir, '.env')) ? rootEnvDir : import.meta.dirname

  const env = loadEnv(mode, envDir, '')
  const backendTarget = process.env.BACKEND_URL || env.BACKEND_URL || 'http://localhost:8080'

  return {
    envDir,
    plugins: [react(), tailwindcss()],
    test: {
      environment: 'jsdom',
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      watch: {
        usePolling: true,
      },
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
  }
})