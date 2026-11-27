import axios from 'axios'

import type { ProjectResponse } from '../types/project'

const API_BASE_URL = '/api'

export class ProjectsUnavailableError extends Error {
  constructor() {
    super('Unable to load projects')
    this.name = 'ProjectsUnavailableError'
  }
}

export async function getProjects(): Promise<ProjectResponse[]> {
  try {
    const response = await axios.get<unknown>(`${API_BASE_URL}/projects`)

    if (!Array.isArray(response.data)) {
      throw new ProjectsUnavailableError()
    }

    return response.data as ProjectResponse[]
  } catch {
    throw new ProjectsUnavailableError()
  }
}
