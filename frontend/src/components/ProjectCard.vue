<script setup lang="ts">
import type { ProjectResponse } from '../types/project'

defineProps<{ project: ProjectResponse }>()

function isHttpsUrl(value: string): boolean {
  try {
    return new URL(value).protocol === 'https:'
  } catch {
    return false
  }
}
</script>

<template>
  <article>
    <h2>
      <a
        v-if="isHttpsUrl(project.htmlUrl)"
        data-test="repository-link"
        :href="project.htmlUrl"
        target="_blank"
        rel="noopener noreferrer"
      >{{ project.name }}</a>
      <template v-else>{{ project.name }}</template>
    </h2>
    <p v-if="project.description !== null" data-test="description">{{ project.description }}</p>
    <span v-if="project.homepage !== null && isHttpsUrl(project.homepage)" data-test="homepage">
      <a
        :href="project.homepage"
        target="_blank"
        rel="noopener noreferrer"
      >{{ project.homepage }}</a>
    </span>
    <span v-if="project.language !== null" data-test="language">{{ project.language }}</span>
    <time v-if="project.pushedAt !== null" data-test="pushed-at" :datetime="project.pushedAt">{{ project.pushedAt }}</time>
  </article>
</template>
