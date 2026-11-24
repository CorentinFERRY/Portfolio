package io.github.corentinferry.selection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(JdbcTemplateAutoConfiguration.class)
class RepositorySelectionRepositoryTest {

	private final RepositorySelectionRepository repository;
	private final TestEntityManager entityManager;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	RepositorySelectionRepositoryTest(RepositorySelectionRepository repository,
			TestEntityManager entityManager, JdbcTemplate jdbcTemplate) {
		this.repository = repository;
		this.entityManager = entityManager;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Test
	void savesSelectionAndReadsItBack() {
		repository.save(new RepositorySelection(42L));
		entityManager.flush();
		entityManager.clear();

		RepositorySelection reloaded = repository.findById(42L).orElseThrow();

		assertEquals(42L, reloaded.githubId());
	}

	@Test
	void duplicateGithubIdViolatesPrimaryKey() {
		jdbcTemplate.update("INSERT INTO repository_selection (github_id) VALUES (?)", 7L);

		assertThrowsExactly(DuplicateKeyException.class,
				() -> jdbcTemplate.update("INSERT INTO repository_selection (github_id) VALUES (?)", 7L));
	}

	@Test
	void nullGithubIdViolatesNotNullConstraint() {
		assertThrowsExactly(DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("INSERT INTO repository_selection (github_id) VALUES (?)", new Object[] { null }));
	}

}
