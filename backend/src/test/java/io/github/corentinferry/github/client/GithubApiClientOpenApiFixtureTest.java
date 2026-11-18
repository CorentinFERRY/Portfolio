package io.github.corentinferry.github.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Provenance of {@code openapi-user-repos-example.json}: official GitHub REST API
 * OpenAPI description, endpoint {@code GET /users/{username}/repos}, response 200
 * example {@code minimal-repository-items}.
 * URL: https://raw.githubusercontent.com/github/rest-api-description/main/descriptions/api.github.com/api.github.com.yaml
 * Downloaded: 2026-11-17. SHA-256: 1852a9284fbd7b2d7eda16b96fac8115255b9ea84805d8bafffd227010270fd4.
 * Description {@code info.version}: 1.1.4. API version used by the client: 2022-11-28.
 * License declared in {@code info.license}: MIT (https://spdx.org/licenses/MIT).
 * The repository LICENSE file was not verified: only the description was downloaded.
 * Fixture SHA-256: f9b4508f02a388fdf6bd7da37443f1728c213788fe706643fcf6edb12373f09e.
 */
class GithubApiClientOpenApiFixtureTest {

	private WireMockServer server;
	private GithubProperties props;

	@BeforeEach
	void setUp() {
		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();
		props = new GithubProperties("test-token", "fictional-owner", "http://localhost:" + server.port(), 3000, 5000);
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void deserializesOfficialExampleAndIgnoresUnknownFields() throws IOException {
		String body = readFixture();
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));

		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();

		assertEquals(1, repos.size());
		var repo = repos.get(0);
		assertEquals("Hello-World", repo.name());
		assertEquals(1296269L, repo.id());
		assertEquals("This your first repo!", repo.description());
		assertEquals("https://github.com/octocat/Hello-World", repo.htmlUrl());
		assertEquals("https://github.com", repo.homepage());
		assertNull(repo.language());
		assertEquals(List.of("octocat", "atom", "electron", "api"), repo.topics());
		assertEquals(80L, repo.stargazersCount());
		assertFalse(repo.fork());
		assertFalse(repo.archived());
		assertEquals("2011-01-26T19:06:43Z", repo.pushedAt());
	}

	private String readFixture() throws IOException {
		try (InputStream stream = getClass().getResourceAsStream("/github/openapi-user-repos-example.json")) {
			assertNotNull(stream, "OpenAPI example fixture is missing from the classpath");
			String body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			assertFalse(body.isBlank(), "OpenAPI example fixture is empty");
			return body;
		}
	}
}
