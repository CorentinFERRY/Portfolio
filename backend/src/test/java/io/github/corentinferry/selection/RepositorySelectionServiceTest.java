package io.github.corentinferry.selection;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(RepositorySelectionService.class)
class RepositorySelectionServiceTest {

	private final RepositorySelectionService service;
	private final RepositorySelectionRepository repository;

	@Autowired
	RepositorySelectionServiceTest(RepositorySelectionService service, RepositorySelectionRepository repository) {
		this.service = service;
		this.repository = repository;
	}

	@Test
	void addThenListContainsGithubId() {
		service.add(42L);

		assertEquals(List.of(42L), service.list());
	}

	@Test
	void addingSameGithubIdTwiceKeepsOneRow() {
		service.add(42L);
		service.add(42L);

		assertEquals(1, repository.count());
	}

	@Test
	void removeDeletesSelection() {
		service.add(42L);

		service.remove(42L);

		assertEquals(List.of(), service.list());
	}

	@Test
	void removingAbsentGithubIdIsNoOp() {
		service.add(42L);

		service.remove(99L);

		assertEquals(List.of(42L), service.list());
	}

	@Test
	void listIsEmptyWhenNothingSelected() {
		List<Long> githubIds = service.list();

		assertNotNull(githubIds);
		assertTrue(githubIds.isEmpty());
	}

	@Test
	void listIsSortedByGithubIdAscending() {
		service.add(30L);
		service.add(10L);
		service.add(20L);

		assertEquals(List.of(10L, 20L, 30L), service.list());
	}

	@ParameterizedTest
	@ValueSource(longs = { 0L, -1L })
	void addRejectsNonPositiveGithubId(long githubId) {
		assertThrowsExactly(IllegalArgumentException.class, () -> service.add(githubId));

		assertEquals(0, repository.count());
	}

}
