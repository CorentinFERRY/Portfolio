package io.github.corentinferry.github.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record GithubRepository(
		long id,
		String name,
		String description,
		String htmlUrl,
		String homepage,
		String language,
		List<String> topics,
		long stargazersCount,
		boolean fork,
		boolean archived,
		String pushedAt
) {
	public GithubRepository {
		topics = topics == null ? Collections.emptyList() : List.copyOf(topics);
	}
}
