import { mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProjectCard from '../components/ProjectCard.vue'
import type { ProjectResponse } from '../types/project'

function makeProject(overrides: Partial<ProjectResponse> = {}): ProjectResponse {
  return {
    id: 424242,
    name: 'repo-name',
    description: 'A description',
    htmlUrl: 'https://example.com/owner/repo-name',
    homepage: 'https://home.example.com',
    language: 'TypeScript',
    pushedAt: '2026-01-02T03:04:05Z',
    ...overrides,
  }
}

function mountCard(overrides: Partial<ProjectResponse> = {}) {
  return mount(ProjectCard, {
    props: { project: makeProject(overrides) },
  })
}

function expectNothingRendered(wrapper: VueWrapper, selector: string): void {
  expect(wrapper.find(selector).exists()).toBe(false)
  expect(wrapper.text()).not.toContain('null')
  expect(wrapper.text()).not.toMatch(/\b(Description|Homepage|Language|Pushed)\b/)

  const emptyElements = wrapper
    .findAll('*')
    .map((element) => element.text().trim())
    .filter((text) => text === '')

  expect(emptyElements).toEqual([])
}

describe('ProjectCard', () => {
  it('renders the name as a repository link with target blank and both rel tokens', () => {
    const project = makeProject()
    const wrapper = mountCard()

    const link = wrapper.get('[data-test="repository-link"]')

    expect(link.text()).toBe(project.name)
    expect(link.attributes('href')).toBe(project.htmlUrl)
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')?.split(/\s+/)).toEqual(['noopener', 'noreferrer'])
  })

  it('renders description, homepage, language and pushedAt from the prop', () => {
    const project = makeProject()
    const wrapper = mountCard()

    expect(wrapper.get('[data-test="description"]').text()).toBe(project.description)

    const homepageLink = wrapper.get('[data-test="homepage"] a')

    expect(homepageLink.text()).toBe(project.homepage)
    expect(homepageLink.attributes('href')).toBe(project.homepage)
    expect(wrapper.get('[data-test="language"]').text()).toBe(project.language)

    const time = wrapper.get('[data-test="pushed-at"]')

    expect(time.text()).toBe(project.pushedAt)
    expect(time.attributes('datetime')).toBe(project.pushedAt)
  })

  it('renders homepage as a link with target blank and both rel tokens', () => {
    const project = makeProject()
    const wrapper = mountCard()

    const link = wrapper.get('[data-test="homepage"] a')

    expect(link.attributes('href')).toBe(project.homepage)
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')?.split(/\s+/)).toEqual(['noopener', 'noreferrer'])
  })

  it('never renders the project id', () => {
    const wrapper = mountCard({ id: 987654321 })

    expect(wrapper.text()).not.toContain('987654321')
    expect(wrapper.html()).not.toContain('987654321')
  })

  it('renders pushedAt unchanged in a time element with its datetime attribute', () => {
    const pushedAt = '2026-01-02T03:04:05.123+02:00'
    const wrapper = mountCard({ pushedAt })

    const time = wrapper.get('[data-test="pushed-at"]')

    expect(time.text()).toBe(pushedAt)
    expect(time.attributes('datetime')).toBe(pushedAt)
  })

  it('renders nothing for a null description', () => {
    const wrapper = mountCard({ description: null })

    expectNothingRendered(wrapper, '[data-test="description"]')
  })

  it('renders nothing for a null homepage', () => {
    const wrapper = mountCard({ homepage: null })

    expectNothingRendered(wrapper, '[data-test="homepage"]')
  })

  it('renders nothing for a null language', () => {
    const wrapper = mountCard({ language: null })

    expectNothingRendered(wrapper, '[data-test="language"]')
  })

  it('renders nothing for a null pushedAt', () => {
    const wrapper = mountCard({ pushedAt: null })

    expectNothingRendered(wrapper, '[data-test="pushed-at"]')
  })

  it('escapes an img onerror payload in name and description', () => {
    const payload = '<img src=x onerror=alert(1)>'
    const wrapper = mountCard({ name: payload, description: payload })

    expect(wrapper.get('[data-test="repository-link"]').text()).toBe(payload)
    expect(wrapper.get('[data-test="description"]').text()).toBe(payload)
    expect(wrapper.findAll('img')).toHaveLength(0)
    expect(wrapper.find('[onerror]').exists()).toBe(false)
  })

  it('renders the name as text when htmlUrl uses javascript:', () => {
    const refusedUrl = 'javascript:alert(1)'
    const wrapper = mountCard({ htmlUrl: refusedUrl })

    expect(wrapper.get('h2').text()).toBe('repo-name')
    expect(wrapper.find('[data-test="repository-link"]').exists()).toBe(false)
    expect(wrapper.findAll('a').map((link) => link.attributes('href'))).not.toContain(refusedUrl)
    expect(wrapper.get('h2').find('a').exists()).toBe(false)
  })

  it('renders the name as text when htmlUrl uses http:', () => {
    const refusedUrl = 'http://example.com/owner/repo-name'
    const wrapper = mountCard({ htmlUrl: refusedUrl })

    expect(wrapper.get('h2').text()).toBe('repo-name')
    expect(wrapper.find('[data-test="repository-link"]').exists()).toBe(false)
    expect(wrapper.findAll('a').map((link) => link.attributes('href'))).not.toContain(refusedUrl)
    expect(wrapper.get('h2').find('a').exists()).toBe(false)
  })

  it('renders the name as text when htmlUrl cannot be parsed', () => {
    const refusedUrl = 'not a url'
    const wrapper = mountCard({ htmlUrl: refusedUrl })

    expect(wrapper.get('h2').text()).toBe('repo-name')
    expect(wrapper.find('[data-test="repository-link"]').exists()).toBe(false)
    expect(wrapper.findAll('a').map((link) => link.attributes('href'))).not.toContain(refusedUrl)
    expect(wrapper.get('h2').find('a').exists()).toBe(false)
  })

  it.each([
    ['javascript:', 'javascript:alert(1)'],
    ['http:', 'http://home.example.com'],
    ['malformed', 'not a url'],
  ])('renders nothing for homepage when it is not a valid https URL: %s', (_label, homepage) => {
    const wrapper = mountCard({ homepage })

    expectNothingRendered(wrapper, '[data-test="homepage"]')
    expect(wrapper.findAll('a').map((link) => link.attributes('href'))).not.toContain(homepage)
  })
})
