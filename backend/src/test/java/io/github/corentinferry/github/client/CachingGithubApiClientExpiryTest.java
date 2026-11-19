package io.github.corentinferry.github.client;

import com.github.benmanes.caffeine.cache.Ticker;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import io.github.corentinferry.github.model.GithubRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CachingGithubApiClientExpiryTest {

	private static final String REPOS_PATH = "/users/fictional-owner/repos";
	private static final int TTL_MS = 1000;

	private WireMockServer server;
	private GithubProperties props;
	private MutableTicker ticker;

	@BeforeEach
	void setUp() {
		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();
		props = new GithubProperties("test-token", "fictional-owner",
				"http://localhost:" + server.port(), 3000, 5000, TTL_MS, 100);
		ticker = new MutableTicker();
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop();
		}
	}

	private GithubApiClient cachingClient() {
		Executor executor = Runnable::run;
		return new CachingGithubApiClient(new DefaultGithubApiClient(props), props, ticker, executor);
	}

	private void stubSuccess() {
		server.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(RepositoryResponses.repositoryPage(1, "repo-one", false))));
	}

	private void stubSuccessThenFailure() {
		server.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.inScenario("expiry")
				.whenScenarioStateIs(Scenario.STARTED)
				.willSetStateTo("expired")
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody(RepositoryResponses.repositoryPage(1, "repo-one", false))));
		server.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.inScenario("expiry")
				.whenScenarioStateIs("expired")
				.willReturn(aResponse().withStatus(500)));
	}

	@Test
	void doesNotExpireBeforeTtl() {
		stubSuccess();
		GithubApiClient client = cachingClient();

		List<GithubRepository> first = client.listPublicRepositories();
		ticker.advance(Duration.ofMillis(TTL_MS - 1L));
		List<GithubRepository> second = client.listPublicRepositories();

		assertEquals(1, first.size());
		assertEquals("repo-one", first.get(0).name());
		assertEquals(first, second);
		server.verify(1, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}

	@Test
	void expiresAfterTtl() {
		stubSuccess();
		GithubApiClient client = cachingClient();

		client.listPublicRepositories();
		ticker.advance(Duration.ofMillis(TTL_MS + 1L));
		List<GithubRepository> second = client.listPublicRepositories();

		assertEquals(1, second.size());
		server.verify(2, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}

	@Test
	void expiredValueIsNotServedWhenGithubFails() {
		stubSuccessThenFailure();
		GithubApiClient client = cachingClient();

		assertEquals(1, client.listPublicRepositories().size());
		server.verify(1, getRequestedFor(urlPathEqualTo(REPOS_PATH)));

		ticker.advance(Duration.ofMillis(TTL_MS + 1L));
		assertThrows(GithubUnavailableException.class, client::listPublicRepositories);
		server.verify(2, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}

	private static final class MutableTicker implements Ticker {
		private long nanos;

		@Override
		public long read() {
			return nanos;
		}

		private void advance(Duration duration) {
			nanos += duration.toNanos();
		}
	}
}
