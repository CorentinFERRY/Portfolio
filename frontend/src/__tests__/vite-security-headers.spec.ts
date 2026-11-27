// @vitest-environment node
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveConfig, type ResolvedConfig } from 'vite'
import { beforeAll, describe, expect, it } from 'vitest'

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const configFile = resolve(frontendRoot, 'vite.config.ts')

const EXPECTED_DOCUMENT_CSP =
  "default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'; " +
  "img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; " +
  "form-action 'self'; frame-ancestors 'none'"

type ServerHeaders = ResolvedConfig['server']['headers']

let devHeaders: ServerHeaders
let previewHeaders: ServerHeaders

// Resolve the config the way `vite` and `vite preview` do, so the tests see
// the headers Vite will actually send, including the preview fallback to
// server.headers.
beforeAll(async () => {
  const inlineConfig = { configFile, root: frontendRoot, logLevel: 'silent' as const }

  devHeaders = (await resolveConfig(inlineConfig, 'serve')).server.headers
  previewHeaders = (
    await resolveConfig(inlineConfig, 'serve', 'production', 'production', true)
  ).preview.headers
})

function cspOf(headers: ServerHeaders): unknown {
  return headers?.['Content-Security-Policy']
}

describe('vite document security headers', () => {
  it('serves the production document csp from vite preview', () => {
    expect(cspOf(previewHeaders)).toBe(EXPECTED_DOCUMENT_CSP)
  })

  it('serves the dev document csp from vite dev server', () => {
    expect(cspOf(devHeaders)).toBe(EXPECTED_DOCUMENT_CSP)
  })

  it('allows only self-hosted fonts', () => {
    for (const [server, headers] of [['dev', devHeaders], ['preview', previewHeaders]] as const) {
      const csp = cspOf(headers)

      expect(csp, `${server} csp`).toBeTypeOf('string')
      const fontDirectives = String(csp)
        .split(';')
        .map((directive) => directive.trim())
        .filter((directive) => directive.startsWith('font-src'))

      expect(fontDirectives, `${server} font-src`).toEqual(["font-src 'self'"])
      expect(csp, `${server} csp`).not.toMatch(/googleapis|gstatic|https:/)
    }
  })

  it('forbids inline and eval scripts in the production csp', () => {
    const csp = cspOf(previewHeaders)

    expect(csp, 'preview csp').toBeTypeOf('string')
    for (const token of ["'unsafe-inline'", "'unsafe-eval'", '*', 'data:']) {
      expect(csp, `preview csp must not contain ${token}`).not.toContain(token)
    }
  })

  it('sends referrer, nosniff and frame options from both servers', () => {
    const expected = {
      'Referrer-Policy': 'no-referrer',
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
    }

    expect(devHeaders, 'dev headers').toMatchObject(expected)
    expect(previewHeaders, 'preview headers').toMatchObject(expected)
  })
})
