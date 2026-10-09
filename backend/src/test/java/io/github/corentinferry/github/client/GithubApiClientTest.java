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

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
		String body = "[{\"id\":1,\"name\":\"fictional-repo\",\"description\":\"desc\",\"html_url\":\"https://example.com/fictional-owner/fictional-repo\",\"homepage\":\"https://example.com\",\"language\":\"Java\",\"topics\":[\"demo\"],\"stargazers_count\":5,\"fork\":false,\"archived\":false,\"pushed_at\":\"2026-10-09T00:00:00Z\",\"private\":false,\"unknown_field\":\"ignored\"}]";
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
	void dropsPrivateRepos() {
		String body = "[{\"id\":1,\"name\":\"r1\",\"private\":true},{\"id\":2,\"name\":\"r2\",\"private\":false,\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody(body)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(1, repos.size());
		assertEquals("r2", repos.get(0).name());
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
}
