// @vitest-environment node
import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const testDir = dirname(fileURLToPath(import.meta.url))
const srcRoot = resolve(testDir, '..')
const frontendRoot = resolve(testDir, '../..')
const selfPath = fileURLToPath(import.meta.url)

const forbidden = [
  { label: 'v-html', pattern: /\bv-html\b/ },
  { label: 'innerHTML', pattern: /\binnerHTML\b/ },
  { label: 'outerHTML', pattern: /\bouterHTML\b/ },
  { label: 'insertAdjacentHTML', pattern: /\binsertAdjacentHTML\b/ },
]

function toFrontendRelative(path: string): string {
  return relative(frontendRoot, path).split(sep).join('/')
}

function walk(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)

    if (entry.isDirectory()) {
      return walk(path)
    }

    return [path]
  })
}

describe('frontend source guard', () => {
  it('rejects raw html sinks in .vue and .ts files', () => {
    const sourceFiles = walk(srcRoot)
      .filter((path) => path !== selfPath && /\.(vue|ts)$/.test(path))
      .sort()

    const readPaths = sourceFiles.map(toFrontendRelative)
    const offenders = sourceFiles.flatMap((path) => {
      const content = readFileSync(path, 'utf8')
      const displayPath = toFrontendRelative(path)

      return forbidden
        .filter(({ pattern }) => pattern.test(content))
        .map(({ label }) => `${displayPath}: ${label}`)
    })

    expect(readPaths, 'guard must read at least one source file').toContain('src/App.vue')
    expect(offenders, `forbidden raw html sinks found: ${offenders.join(', ')}`).toEqual([])
  })
})
