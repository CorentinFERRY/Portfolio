package io.github.corentinferry.controller;

import io.github.corentinferry.project.ProjectResponse;
import io.github.corentinferry.project.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping("/api/projects")
	public List<ProjectResponse> listProjects() {
		return projectService.listProjects();
	}
}
