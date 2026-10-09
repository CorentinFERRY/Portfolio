package io.github.corentinferry.github.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubRepositoryTest {

	@Test
	void createsWithDefaultsForNullTopics() {
		GithubRepository repo = new GithubRepository(1L, "name", null, "url", null, null, null, 0, false, false, null);
		assertTrue(repo.topics().isEmpty());
	}

	@Test
	void topicsAreImmutableCopy() {
		GithubRepository repo = new GithubRepository(1L, "name", null, "url", null, null, List.of("a"), 0, false, false, null);
		assertEquals(1, repo.topics().size());
	}
}
