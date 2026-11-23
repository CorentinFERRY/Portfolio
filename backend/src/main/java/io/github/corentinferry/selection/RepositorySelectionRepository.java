package io.github.corentinferry.selection;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists selections by stable GitHub id, so repository renames do not require an update.
 */
public interface RepositorySelectionRepository extends JpaRepository<RepositorySelection, Long> {
}
