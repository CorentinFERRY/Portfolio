package io.github.corentinferry.github.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubPaginationViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(OutputCaptureExtension.class)
class GithubApiClientPaginationTest {

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

	private void stubFirstPageWithNextLink(String nextUrl) {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("1"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Link", "<" + nextUrl + ">; rel=\"next\"")
						.withBody(RepositoryResponses.singlePublicRepositoryPage())));
	}

	private void verifyOnlyPageOneWasRequested() {
		server.verify(1, getRequestedFor(urlPathEqualTo("/users/fictional-owner/repos")));
	}

	@Test
	void followsPagination() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("1"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Link", "<http://localhost:" + server.port() + "/users/fictional-owner/repos?page=2>; rel=\"next\"")
						.withBody(RepositoryResponses.repositoryPage(1, "r1", false))));
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("2"))
				.willReturn(aResponse().withStatus(200).withBody(RepositoryResponses.repositoryPage(2, "r2", false))));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(2, repos.size());
		server.verify(2, getRequestedFor(urlPathEqualTo("/users/fictional-owner/repos")));
	}

	@Test
	void throwsWhenNextLinkHasDifferentHost() {
		stubFirstPageWithNextLink("http://evil.example/users/fictional-owner/repos?page=2");
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		verifyOnlyPageOneWasRequested();
	}

	@Test
	void throwsWhenNextLinkHostStartsWithBaseHost() {
		stubFirstPageWithNextLink("http://localhost.evil.example:" + server.port() + "/users/fictional-owner/repos?page=2");
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		verifyOnlyPageOneWasRequested();
	}

	@Test
	void throwsWhenNextLinkHasDifferentPort() {
		stubFirstPageWithNextLink("http://localhost:" + (server.port() + 1) + "/users/fictional-owner/repos?page=2");
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		verifyOnlyPageOneWasRequested();
	}

	@Test
	void throwsWhenNextLinkHasDifferentScheme() {
		stubFirstPageWithNextLink("https://localhost:" + server.port() + "/users/fictional-owner/repos?page=2");
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		verifyOnlyPageOneWasRequested();
	}

	@Test
	void throwsWhenNextLinkHasCredentials() {
		stubFirstPageWithNextLink("http://user@localhost:" + server.port() + "/users/fictional-owner/repos?page=2");
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		verifyOnlyPageOneWasRequested();
	}

	@Test
	void throwsWhenNextLinkHostHasTrailingDot() {
		stubFirstPageWithNextLink("http://localhost.:" + server.port() + "/users/fictional-owner/repos?page=2");
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		verifyOnlyPageOneWasRequested();
	}

	@Test
	void throwsWhenPageCapExceededAndMakesExactlyTenRequests() {
		for (int i = 1; i <= 10; i++) {
			final int page = i;
			server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
					.withQueryParam("page", equalTo(String.valueOf(page)))
					.willReturn(aResponse().withStatus(200)
							.withHeader("Link", "<http://localhost:" + server.port() + "/users/fictional-owner/repos?page=" + (page + 1) + ">; rel=\"next\"")
							.withBody(RepositoryResponses.singlePublicRepositoryPage())));
		}
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		server.verify(10, getRequestedFor(urlPathEqualTo("/users/fictional-owner/repos")));
		server.verify(0, getRequestedFor(urlPathEqualTo("/users/fictional-owner/repos")).withQueryParam("page", equalTo("11")));
	}
}
