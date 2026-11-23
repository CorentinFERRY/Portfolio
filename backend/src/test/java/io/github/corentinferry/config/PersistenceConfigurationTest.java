package io.github.corentinferry.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PersistenceConfigurationTest {

	private final DataSource dataSource;

	@Autowired
	PersistenceConfigurationTest(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Test
	void datasourceComesFromTestProfileConfiguration() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			String url = connection.getMetaData().getURL();
			assertTrue(url.contains("portfolio-test"),
					"expected datasource URL from application-test.yml, got " + url);
		}
	}

	@Test
	void schemaCreatesRepositorySelectionTable() throws SQLException {
		try (Connection connection = dataSource.getConnection();
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM repository_selection")) {
			assertTrue(resultSet.next());
			assertEquals(0L, resultSet.getLong(1));
		}
	}

}
