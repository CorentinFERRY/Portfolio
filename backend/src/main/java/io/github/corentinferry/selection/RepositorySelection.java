package io.github.corentinferry.selection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stores only the stable GitHub repository id, so renaming a repository cannot break a selection.
 */
@Entity
@Table(name = "repository_selection")
public class RepositorySelection {

	@Id
	@Column(name = "github_id")
	private long githubId;

	protected RepositorySelection() {
	}

	public RepositorySelection(long githubId) {
		this.githubId = githubId;
	}

	public long githubId() {
		return githubId;
	}

}
