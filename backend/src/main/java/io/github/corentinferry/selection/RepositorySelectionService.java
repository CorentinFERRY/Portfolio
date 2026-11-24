package io.github.corentinferry.selection;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the selection rules before persisting stable GitHub ids.
 */
@Service
public class RepositorySelectionService {

	private final RepositorySelectionRepository repository;

	public RepositorySelectionService(RepositorySelectionRepository repository) {
		this.repository = repository;
	}

	/**
	 * Stores an id only once, so repeated publishing cannot create duplicate rows.
	 */
	@Transactional
	public void add(long githubId) {
		if (githubId <= 0) {
			throw new IllegalArgumentException("githubId must be positive");
		}
		if (!repository.existsById(githubId)) {
			repository.save(new RepositorySelection(githubId));
		}
	}

	/**
	 * Ignores unknown ids so unpublishing an absent selection is harmless.
	 */
	@Transactional
	public void remove(long githubId) {
		repository.findById(githubId).ifPresent(repository::delete);
	}

	/**
	 * Returns a stable order for the public cards without exposing a mutable list.
	 */
	@Transactional(readOnly = true)
	public List<Long> list() {
		return repository.findAll().stream()
				.sorted(Comparator.comparingLong(RepositorySelection::githubId))
				.map(RepositorySelection::githubId)
				.toList();
	}

}
