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

// srcdoc is case-insensitive because HTML attributes are and JSX spells it srcDoc.
const additionalForbidden = [
  { label: 'document.write', pattern: /\bdocument\.write(?:ln)?\b/ },
  { label: 'setHTMLUnsafe', pattern: /\bsetHTMLUnsafe\b/ },
  { label: 'createContextualFragment', pattern: /\bcreateContextualFragment\b/ },
  { label: 'srcdoc', pattern: /\bsrcdoc\b/i },
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

function sourceFilesMatching(extensions: RegExp): string[] {
  return walk(srcRoot)
    .filter((path) => path !== selfPath && extensions.test(path))
    .sort()
}

function findOffenders(
  sourceFiles: string[],
  patterns: { label: string; pattern: RegExp }[],
): string[] {
  return sourceFiles.flatMap((path) => {
    const content = readFileSync(path, 'utf8')
    const displayPath = toFrontendRelative(path)

    return patterns
      .filter(({ pattern }) => pattern.test(content))
      .map(({ label }) => `${displayPath}: ${label}`)
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

  // No such file exists today: only mutations prove these extensions are scanned.
  it('rejects raw html sinks in .js, .mjs, .jsx and .tsx files', () => {
    const sourceFiles = sourceFilesMatching(/\.(js|mjs|jsx|tsx)$/)
    const offenders = findOffenders(sourceFiles, [...forbidden, ...additionalForbidden])

    expect(offenders, `forbidden raw html sinks found: ${offenders.join(', ')}`).toEqual([])
  })

  it('rejects document.write, setHTMLUnsafe, createContextualFragment and srcdoc', () => {
    const sourceFiles = sourceFilesMatching(/\.(vue|ts|js|mjs|jsx|tsx)$/)
    const readPaths = sourceFiles.map(toFrontendRelative)
    const offenders = findOffenders(sourceFiles, additionalForbidden)

    expect(readPaths, 'guard must read at least one source file').toContain('src/App.vue')
    expect(offenders, `forbidden raw html sinks found: ${offenders.join(', ')}`).toEqual([])
  })
})
