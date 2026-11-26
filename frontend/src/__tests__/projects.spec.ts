import axios, { type AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getProjects, ProjectsUnavailableError } from '../services/projects'

vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
  },
}))

const get = vi.mocked(axios.get)

const projects = [
  {
    id: 1,
    name: 'one',
    description: null,
    htmlUrl: 'https://example.com/one',
    homepage: null,
    language: 'TypeScript',
    pushedAt: '2026-01-01T00:00:00Z',
  },
]

function responseWith(data: unknown): AxiosResponse<unknown> {
  return { data } as AxiosResponse<unknown>
}

function expectProjectsError(error: unknown): void {
  expect(error).toBeInstanceOf(ProjectsUnavailableError)
  const projectsError = error as Error & { cause?: unknown }

  expect(projectsError.message).toBe('Unable to load projects')
  expect(projectsError.message).not.toContain('SECRET_UPSTREAM_DETAIL')
  expect(projectsError.cause).toBeUndefined()
}

describe('getProjects', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests GET /api/projects with a relative url', async () => {
    get.mockResolvedValue(responseWith(projects))

    await getProjects()

    expect(get).toHaveBeenCalledTimes(1)
    expect(get).toHaveBeenCalledWith('/api/projects')
  })

  it('returns the response array unchanged', async () => {
    get.mockResolvedValue(responseWith(projects))

    await expect(getProjects()).resolves.toEqual(projects)
  })

  it('rejects with the generic error on 503', async () => {
    get.mockRejectedValue(
      Object.assign(new Error('upstream failed'), {
        response: { status: 503, data: { error: 'SECRET_UPSTREAM_DETAIL' } },
      }),
    )

    const error = await getProjects().catch((caught: unknown) => caught)

    expectProjectsError(error)
  })

  it('rejects with the generic error on 502', async () => {
    get.mockRejectedValue(
      Object.assign(new Error('upstream failed'), {
        response: { status: 502, data: { error: 'SECRET_UPSTREAM_DETAIL' } },
      }),
    )

    const error = await getProjects().catch((caught: unknown) => caught)

    expectProjectsError(error)
  })

  it('rejects with the generic error on a network failure', async () => {
    get.mockRejectedValue(new Error('network SECRET_UPSTREAM_DETAIL'))

    const error = await getProjects().catch((caught: unknown) => caught)

    expectProjectsError(error)
  })

  it.each([
    ['object', { error: 'SECRET_UPSTREAM_DETAIL' }],
    ['string', 'SECRET_UPSTREAM_DETAIL'],
    ['null', null],
    ['undefined', undefined],
  ])('rejects with the generic error when data is not an array: %s', async (_label, data) => {
    get.mockResolvedValue(responseWith(data))

    const error = await getProjects().catch((caught: unknown) => caught)

    expectProjectsError(error)
  })
})
