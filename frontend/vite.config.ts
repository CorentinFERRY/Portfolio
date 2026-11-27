import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

import { DEV_DOCUMENT_CSP, DOCUMENT_CSP, documentHeaders } from './security-headers.ts'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    headers: documentHeaders(DEV_DOCUMENT_CSP),
    proxy: {
      '/api': {
        target: process.env.PORTFOLIO_BACKEND_URL ?? 'http://127.0.0.1:8080',
      },
    },
  },
  // Set explicitly: without it, preview would fall back to server.headers and
  // serve the dev policy.
  preview: {
    headers: documentHeaders(DOCUMENT_CSP),
  },
})
