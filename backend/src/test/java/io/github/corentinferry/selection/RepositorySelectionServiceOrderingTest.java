package io.github.corentinferry.selection;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositorySelectionServiceOrderingTest {

	@Test
	void listSortsByGithubIdAscending() {
		RepositorySelectionRepository repository = mock(RepositorySelectionRepository.class);
		when(repository.findAll()).thenReturn(List.of(
				new RepositorySelection(30L),
				new RepositorySelection(10L),
				new RepositorySelection(20L)));
		RepositorySelectionService service = new RepositorySelectionService(repository);

		assertEquals(List.of(10L, 20L, 30L), service.list());
	}

}
