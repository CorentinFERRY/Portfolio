import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ProjectCard from '../components/ProjectCard.vue'
import ProjectList from '../components/ProjectList.vue'
import { getProjects, ProjectsUnavailableError } from '../services/projects'
import type { ProjectResponse } from '../types/project'

vi.mock('../services/projects', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/projects')>()),
  getProjects: vi.fn(),
}))

const getProjectsMock = vi.mocked(getProjects)

const LOADING_TEXT = 'Chargement des projets…'
const ERROR_TEXT = 'Impossible de charger les projets pour le moment.'
const EMPTY_TEXT = 'Aucun projet publié pour le moment.'

function project(id: number, name: string): ProjectResponse {
  return {
    id,
    name,
    description: null,
    htmlUrl: `https://github.com/example/${name}`,
    homepage: null,
    language: null,
    pushedAt: null,
  }
}

// Deliberately neither sorted by id nor by name, so any reordering is visible.
const projects = [project(3, 'gamma'), project(1, 'alpha'), project(2, 'beta')]

describe('ProjectList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows the loading status while projects are pending', async () => {
    getProjectsMock.mockReturnValue(new Promise<ProjectResponse[]>(() => {}))

    const wrapper = mount(ProjectList)
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toBe(LOADING_TEXT)
    expect(wrapper.find('ul').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('requests projects exactly once on mount', async () => {
    getProjectsMock.mockResolvedValue(projects)

    mount(ProjectList)
    await flushPromises()

    expect(getProjectsMock).toHaveBeenCalledTimes(1)
  })

  it('renders one ProjectCard per project in api order with its project prop', async () => {
    getProjectsMock.mockResolvedValue(projects)

    const wrapper = mount(ProjectList)
    await flushPromises()

    const cards = wrapper.findAllComponents(ProjectCard)
    expect(cards.map((card) => card.props('project'))).toEqual(projects)
    expect(wrapper.findAll('ul > li')).toHaveLength(projects.length)
  })

  it('hides the loading status after a successful load', async () => {
    getProjectsMock.mockResolvedValue(projects)

    const wrapper = mount(ProjectList)
    await flushPromises()

    expect(wrapper.text()).not.toContain(LOADING_TEXT)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows the empty message and no list for an empty array', async () => {
    getProjectsMock.mockResolvedValue([])

    const wrapper = mount(ProjectList)
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toBe(EMPTY_TEXT)
    expect(wrapper.find('ul').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain(LOADING_TEXT)
  })

  it('shows the generic alert and no list when loading fails', async () => {
    getProjectsMock.mockRejectedValue(new ProjectsUnavailableError())

    const wrapper = mount(ProjectList)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(ERROR_TEXT)
    expect(wrapper.find('ul').exists()).toBe(false)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Unable to load projects')
    expect(wrapper.text()).not.toContain(LOADING_TEXT)
  })

  it('never displays the error message from the rejection', async () => {
    getProjectsMock.mockRejectedValue(new Error('SECRET_UPSTREAM_DETAIL'))

    const wrapper = mount(ProjectList)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(ERROR_TEXT)
    expect(wrapper.html()).not.toContain('SECRET_UPSTREAM_DETAIL')
  })
})
