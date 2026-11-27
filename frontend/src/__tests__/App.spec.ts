import { describe, expect, it } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'

// ProjectList is stubbed so mounting App never triggers a projects request.
function mountApp() {
  return mount(App, { global: { stubs: { ProjectList: true } } })
}

describe('App', () => {
  it('renders the portfolio heading', () => {
    const wrapper = mountApp()

    expect(wrapper.get('h1').text()).toBe('Portfolio')
  })

  it('mounts the project list', () => {
    const wrapper = mountApp()

    expect(wrapper.findComponent({ name: 'ProjectList' }).exists()).toBe(true)
    expect(wrapper.html()).toContain('<project-list-stub')
  })
})
