package io.github.corentinferry.github.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubRepositoryTest {

	@Test
	void createsWithDefaultsForNullTopics() {
		GithubRepository repo = new GithubRepository(1L, "name", null, "url", null, null, null, 0, false, false, null);
		assertTrue(repo.topics().isEmpty());
	}

	@Test
	void topicsAreImmutableCopy() {
		List<String> source = new ArrayList<>(List.of("a"));
		GithubRepository repo = new GithubRepository(1L, "name", null, "url", null, null, source, 0, false, false, null);
		assertThrows(UnsupportedOperationException.class, () -> repo.topics().add("b"));
	}

	@Test
	void topicsAreDetachedFromSource() {
		List<String> source = new ArrayList<>(List.of("a"));
		GithubRepository repo = new GithubRepository(1L, "name", null, "url", null, null, source, 0, false, false, null);

		source.add("b");

		assertEquals(List.of("a"), repo.topics());
	}
}
