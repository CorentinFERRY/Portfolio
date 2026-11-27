import type { OutgoingHttpHeaders } from 'node:http'

/**
 * Content Security Policy of the HTML document. Everything is self-hosted:
 * no inline or eval script, no external font, image or connection.
 * The container server of a later slice must send this exact value.
 */
export const DOCUMENT_CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self'",
  "font-src 'self'",
  "img-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ')

/**
 * Policy of the dev server. Identical today; kept separate so that a future
 * relaxation needed by HMR-injected styles stays limited to development.
 */
export const DEV_DOCUMENT_CSP = DOCUMENT_CSP

/** Security headers sent with every response served by Vite itself. */
export function documentHeaders(contentSecurityPolicy: string): OutgoingHttpHeaders {
  return {
    'Content-Security-Policy': contentSecurityPolicy,
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
  }
}
