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

	@Test
	void followsPagination() {
		String page1 = "[{\"id\":1,\"name\":\"r1\",\"private\":false,\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		String page2 = "[{\"id\":2,\"name\":\"r2\",\"private\":false,\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("1"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Link", "<http://localhost:" + server.port() + "/users/fictional-owner/repos?page=2>; rel=\"next\"")
						.withBody(page1)));
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("2"))
				.willReturn(aResponse().withStatus(200).withBody(page2)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		var repos = client.listPublicRepositories();
		assertEquals(2, repos.size());
	}

	@Test
	void throwsWhenNextLinkHasDifferentHost() {
		String page1 = "[{\"id\":1,\"name\":\"r1\",\"private\":false,\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("1"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Link", "<http://evil.example/users/fictional-owner/repos?page=2>; rel=\"next\"")
						.withBody(page1)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
	}

	@Test
	void throwsWhenPageCapExceeded() {
		String page1 = "[{\"id\":1,\"name\":\"r1\",\"private\":false,\"html_url\":\"u\",\"stargazers_count\":0,\"fork\":false,\"archived\":false}]";
		// Set up so that after fetching up to cap, next still exists
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("1"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Link", "<http://localhost:" + server.port() + "/users/fictional-owner/repos?page=10>; rel=\"next\"")
						.withBody(page1)));
		// But our impl fetches sequentially - easier to simulate by having page9 link to page10? Or adjust
		// Simpler: stub pages 1-10 all with next to 11, and page 10 returns no next? No easier way - let us stub flexibly
		for (int i = 1; i < 10; i++) {
			final int page = i;
			server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
					.withQueryParam("page", equalTo(String.valueOf(page)))
					.willReturn(aResponse().withStatus(200)
							.withHeader("Link", "<http://localhost:" + server.port() + "/users/fictional-owner/repos?page=" + (page + 1) + ">; rel=\"next\"")
							.withBody(page1)));
		}
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.withQueryParam("page", equalTo("10"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Link", "<http://localhost:" + server.port() + "/users/fictional-owner/repos?page=11>; rel=\"next\"")
						.withBody(page1)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubPaginationViolationException.class, () -> client.listPublicRepositories());
		server.verify(0, getRequestedFor(urlPathEqualTo("/users/fictional-owner/repos")).withQueryParam("page", equalTo("11")));
	}
}
