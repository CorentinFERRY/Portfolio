package io.github.corentinferry.project;

public record ProjectResponse(
		long id,
		String name,
		String description,
		String htmlUrl,
		String homepage,
		String language,
		String pushedAt
) {
}
