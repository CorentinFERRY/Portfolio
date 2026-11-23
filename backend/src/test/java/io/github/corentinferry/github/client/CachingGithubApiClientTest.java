package io.github.corentinferry.github.client;

import com.github.benmanes.caffeine.cache.Ticker;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingGithubApiClientTest {

	private WireMockServer server;
	private GithubProperties props;

	@BeforeEach
	void setUp() {
		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();
		props = new GithubProperties("test-token", "fictional-owner",
				"http://localhost:" + server.port(), 3000, 5000);
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop();
		}
	}

	private GithubApiClient cachingClient() {
		Ticker ticker = new FakeTicker();
		Executor executor = Runnable::run;
		return new CachingGithubApiClient(new DefaultGithubApiClient(props), props, ticker, executor);
	}

	@Test
	void secondCallDoesNotSendRequest() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(RepositoryResponses.repositoryPage(1, "repo-one", false))));
		GithubApiClient client = cachingClient();

		List<io.github.corentinferry.github.model.GithubRepository> first = client.listPublicRepositories();
		List<io.github.corentinferry.github.model.GithubRepository> second = client.listPublicRepositories();

		assertEquals(1, first.size());
		assertEquals("repo-one", first.get(0).name());
		assertEquals(first, second);
		server.verify(1, getRequestedFor(urlPathEqualTo("/users/fictional-owner/repos")));
	}

	@Test
	void cachedListIsImmutable() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(RepositoryResponses.repositoryPage(1, "repo-one", false))));

		List<io.github.corentinferry.github.model.GithubRepository> repos = cachingClient().listPublicRepositories();

		assertEquals(1, repos.size());
		assertThrows(UnsupportedOperationException.class, () -> repos.add(repos.get(0)));
	}

	@Test
	void emptyResultIsCached() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("[]")));
		GithubApiClient client = cachingClient();

		assertTrue(client.listPublicRepositories().isEmpty());
		assertTrue(client.listPublicRepositories().isEmpty());
		server.verify(1, getRequestedFor(urlPathEqualTo("/users/fictional-owner/repos")));
	}

	private static final class FakeTicker implements Ticker {
		@Override
		public long read() {
			return 0L;
		}
	}
}
