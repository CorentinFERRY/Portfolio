<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getProjects } from '../services/projects'
import type { ProjectResponse } from '../types/project'
import ProjectCard from './ProjectCard.vue'

// One discriminated state so two states can never be rendered at once.
type ListState =
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'empty' }
  | { kind: 'success'; projects: ProjectResponse[] }

const state = ref<ListState>({ kind: 'loading' })

onMounted(async () => {
  try {
    const projects = await getProjects()
    state.value = projects.length === 0 ? { kind: 'empty' } : { kind: 'success', projects }
  } catch {
    // The error is never read: the page shows a constant message, never technical details.
    state.value = { kind: 'error' }
  }
})
</script>

<template>
  <p v-if="state.kind === 'loading'" role="status">Chargement des projets…</p>
  <p v-else-if="state.kind === 'error'" role="alert">Impossible de charger les projets pour le moment.</p>
  <p v-else-if="state.kind === 'empty'" role="status">Aucun projet publié pour le moment.</p>
  <ul v-else>
    <li v-for="project in state.projects" :key="project.id">
      <ProjectCard :project="project" />
    </li>
  </ul>
</template>
