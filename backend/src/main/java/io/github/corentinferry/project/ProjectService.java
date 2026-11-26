package io.github.corentinferry.project;

import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.github.model.GithubRepository;
import io.github.corentinferry.selection.RepositorySelectionService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

	private final GithubApiClient githubApiClient;
	private final RepositorySelectionService selectionService;

	public ProjectService(GithubApiClient githubApiClient, RepositorySelectionService selectionService) {
		this.githubApiClient = githubApiClient;
		this.selectionService = selectionService;
	}

	public List<ProjectResponse> listProjects() {
		Set<Long> selectedIds = new HashSet<>(selectionService.list());
		if (selectedIds.isEmpty()) {
			return List.of();
		}
		List<GithubRepository> repositories = githubApiClient.listPublicRepositories();
		List<ProjectResponse> projects = new ArrayList<>();
		Set<Long> seenIds = new HashSet<>();
		for (GithubRepository repository : repositories) {
			if (!selectedIds.contains(repository.id()) || !seenIds.add(repository.id())) {
				continue;
			}
			String htmlUrl = httpsUrl(repository.htmlUrl());
			if (htmlUrl == null) {
				continue;
			}
			projects.add(new ProjectResponse(repository.id(), repository.name(), repository.description(),
					htmlUrl, httpsUrl(repository.homepage()), repository.language(), repository.pushedAt()));
		}
		return projects.stream()
				.sorted(Comparator.comparing(ProjectResponse::pushedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
	}

	private static String httpsUrl(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			URI uri = new URI(value);
			String scheme = uri.getScheme();
			if (scheme == null || !scheme.equalsIgnoreCase("https") || uri.getHost() == null) {
				return null;
			}
			return value;
		} catch (URISyntaxException | IllegalArgumentException e) {
			return null;
		}
	}
}
