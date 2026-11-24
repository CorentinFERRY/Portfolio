package io.github.corentinferry.github.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubUnexpectedResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class GithubApiClientTest {

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
	void listsPublicRepositoriesFiltersAndMapsFields() {
		String body = RepositoryResponses.repositoryPage(1, "fictional-repo", false);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("type", equalTo("owner"))
				.withQueryParam("sort", equalTo("pushed"))
				.withQueryParam("direction", equalTo("desc"))
				.withQueryParam("per_page", equalTo("100"))
				.withQueryParam("page", equalTo("1"))
				.withHeader("Authorization", equalTo("Bearer test-token"))
				.withHeader("User-Agent", WireMock.matching(".*"))
				.withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
				.willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(1, repos.size());
		assertEquals("fictional-repo", repos.get(0).name());
		assertEquals(5, repos.get(0).stargazersCount());
	}

	@Test
	void sendsExactUserAgentHeader() {
		String body = RepositoryResponses.repositoryPage(1, "fictional-repo", false);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withHeader("User-Agent", equalTo("portfolio-backend"))
				.willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(1, repos.size());
		assertEquals("fictional-repo", repos.get(0).name());
	}

	private GithubProperties propsWithOwner(String owner) {
		return new GithubProperties("test-token", "fictional-owner",
				"http://localhost:" + server.port(), 3000, 5000) {
			@Override
			public String getOwner() {
				return owner;
			}
		};
	}

	private String requestUrlAfterListing(GithubProperties properties) {
		server.stubFor(get(WireMock.anyUrl()).willReturn(aResponse().withStatus(200).withBody("[]")));
		new DefaultGithubApiClient(properties).listPublicRepositories();
		return server.getAllServeEvents().get(0).getRequest().getUrl();
	}

	@Test
	void encodesQuestionMarkInOwnerAsUriTemplateVariable() {
		String url = requestUrlAfterListing(propsWithOwner("fictional-owner?type=evil"));
		assertTrue(url.startsWith("/users/fictional-owner%3Ftype%3Devil/repos?"), url);
		assertTrue(url.contains("type=owner"), url);
		assertFalse(url.contains("type=evil"), url);
	}

	@Test
	void encodesSlashInOwnerAsUriTemplateVariable() {
		String url = requestUrlAfterListing(propsWithOwner("fictional-owner/extra"));
		assertTrue(url.startsWith("/users/fictional-owner%2Fextra/repos?"), url);
	}

	@Test
	void keepsBasePathPrefixWithoutTrailingSlash() {
		GithubProperties prefixed = new GithubProperties("test-token", "fictional-owner",
				"http://localhost:" + server.port() + "/api/v3", 3000, 5000);
		String url = requestUrlAfterListing(prefixed);
		assertTrue(url.startsWith("/api/v3/users/fictional-owner/repos?"), url);
	}

	@Test
	void keepsBasePathPrefixWithTrailingSlash() {
		GithubProperties prefixed = new GithubProperties("test-token", "fictional-owner",
				"http://localhost:" + server.port() + "/api/v3/", 3000, 5000);
		String url = requestUrlAfterListing(prefixed);
		assertTrue(url.startsWith("/api/v3/users/fictional-owner/repos?"), url);
	}

	@Test
	void dropsPrivateRepos() {
		String body = "[" + RepositoryResponses.repositoryJson(1, "r1", true) + "," + RepositoryResponses.repositoryJson(2, "r2", false) + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(1, repos.size());
		assertEquals("r2", repos.get(0).name());
	}

	@Test
	void dropsRepositoriesWithNonPublicVisibility() {
		String body = "[" + RepositoryResponses.repositoryJson(1, "internal", false, "\"internal\"")
				+ "," + RepositoryResponses.repositoryJson(2, "private", false, "\"private\"") + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertTrue(client.listPublicRepositories().isEmpty());
	}

	@Test
	void keepsRepositoryWithPublicVisibility() {
		String body = "[" + RepositoryResponses.repositoryJson(1, "r1", false, "\"public\"") + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(1, repos.size());
		assertEquals("r1", repos.get(0).name());
	}

	@Test
	void nullVisibilityFallsBackToPrivateFlag() {
		String body = "[" + RepositoryResponses.repositoryJson(1, "public-repo", false, "null")
				+ "," + RepositoryResponses.repositoryJson(2, "private-repo", true, "null") + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(1, repos.size());
		assertEquals("public-repo", repos.get(0).name());
	}

	@Test
	void dropsPrivateRepositoryEvenWithPublicVisibility() {
		String body = "[" + RepositoryResponses.repositoryJson(1, "r1", true, "\"public\"") + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertTrue(client.listPublicRepositories().isEmpty());
	}

	@Test
	void visibilityIsCaseSensitive() {
		String body = "[" + RepositoryResponses.repositoryJson(1, "r1", false, "\"Public\"") + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertTrue(client.listPublicRepositories().isEmpty());
	}

	@Test
	void nonTextualVisibilityThrows() {
		String body = "[" + RepositoryResponses.repositoryJson(1, "r1", false, "123") + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories());
	}

	@Test
	void missingPrivateFieldWithPublicVisibilityThrows() {
		String body = "[{\"id\":1,\"name\":\"r1\",\"visibility\":\"public\",\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories());
	}

	@Test
	void missingPrivateFieldThrows() {
		String body = "[{\"id\":1,\"name\":\"r1\",\"html_url\":\"u\"}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories());
	}

	@Test
	void missingTopicsGivesEmptyList() {
		String body = "[{\"id\":1,\"name\":\"r1\",\"private\":false,\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertTrue(repos.get(0).topics().isEmpty());
	}

	@Test
	void nullFieldsAccepted() {
		String body = "[{\"id\":1,\"name\":\"r1\",\"description\":null,\"html_url\":\"u\",\"homepage\":null,\"language\":null,\"private\":false,\"stargazers_count\":0,\"fork\":false,\"archived\":false,\"pushed_at\":null}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(1, repos.size());
	}

	@Test
	void wrongTypeThrows() {
		String body = "[{\"id\":\"bad\",\"name\":\"r1\",\"private\":false,\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories());
	}

	@Test
	void notArrayThrows() {
		String body = "{\"message\":\"oops\"}";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories());
	}

	@Test
	void emptyArrayReturnsEmpty() {
		String body = "[]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertTrue(client.listPublicRepositories().isEmpty());
	}

	private static Stream<Arguments> nonStringFields() {
		return Stream.of(
				Arguments.of("name", "\"name\":\"r1\"", "\"name\":42"),
				Arguments.of("html_url", "\"html_url\":\"https://example.com/fictional-owner/r1\"", "\"html_url\":42"),
				Arguments.of("description", "\"description\":\"desc\"", "\"description\":42"),
				Arguments.of("homepage", "\"homepage\":\"https://example.com\"", "\"homepage\":42"),
				Arguments.of("language", "\"language\":\"Java\"", "\"language\":42"),
				Arguments.of("pushed_at", "\"pushed_at\":\"2026-10-09T00:00:00Z\"", "\"pushed_at\":42"),
				Arguments.of("topics[0]", "\"topics\":[\"demo\"]", "\"topics\":[42]"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("nonStringFields")
	void nonStringFieldsThrow(String caseName, String validFragment, String invalidFragment) {
		String body = "[" + RepositoryResponses.repositoryJson(1, "r1", false)
				.replace(validFragment, invalidFragment) + "]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories(), caseName);
	}
}
