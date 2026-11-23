package io.github.corentinferry.github.client;

import com.github.benmanes.caffeine.cache.Ticker;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubRateLimitExceededException;
import io.github.corentinferry.github.model.GithubRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingGithubApiClientConcurrencyTest {

	private static final int CALLERS = 8;
	private static final String REPOS_PATH = "/users/fictional-owner/repos";

	private WireMockServer server;
	private GithubProperties props;
	private ExecutorService callerPool;
	private ExecutorService loadingPool;

	@BeforeEach
	void setUp() {
		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();
		props = new GithubProperties("test-token", "fictional-owner",
				"http://localhost:" + server.port(), 3000, 5000);
		callerPool = Executors.newFixedThreadPool(CALLERS);
		loadingPool = Executors.newSingleThreadExecutor();
	}

	@AfterEach
	void tearDown() {
		callerPool.shutdownNow();
		loadingPool.shutdownNow();
		if (server != null) {
			server.stop();
		}
	}

	@RepeatedTest(20)
	void concurrentFailuresTriggerOneRequestAndAllCallersFail() throws Exception {
		server.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(429).withFixedDelay(200)));
		GithubApiClient client = new CachingGithubApiClient(new DefaultGithubApiClient(props), props,
				Ticker.systemTicker(), loadingPool);
		CountDownLatch ready = new CountDownLatch(CALLERS);
		CountDownLatch start = new CountDownLatch(1);
		Callable<List<GithubRepository>> call = () -> {
			ready.countDown();
			if (!start.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("start latch was not released");
			}
			return client.listPublicRepositories();
		};
		List<Future<List<GithubRepository>>> futures = new ArrayList<>();
		for (int i = 0; i < CALLERS; i++) {
			futures.add(callerPool.submit(call));
		}
		assertTrue(ready.await(10, TimeUnit.SECONDS));
		start.countDown();
		for (Future<List<GithubRepository>> future : futures) {
			ExecutionException failure = assertThrows(ExecutionException.class,
					() -> future.get(10, TimeUnit.SECONDS));
			assertEquals(GithubRateLimitExceededException.class, failure.getCause().getClass());
		}
		server.verify(1, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}

	@RepeatedTest(20)
	void concurrentSuccessesTriggerOneRequest() throws Exception {
		server.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(200).withFixedDelay(200)
						.withHeader("Content-Type", "application/json")
						.withBody(RepositoryResponses.repositoryPage(1, "repo-one", false))));
		GithubApiClient client = new CachingGithubApiClient(new DefaultGithubApiClient(props), props,
				Ticker.systemTicker(), loadingPool);
		CountDownLatch ready = new CountDownLatch(CALLERS);
		CountDownLatch start = new CountDownLatch(1);
		Callable<List<GithubRepository>> call = () -> {
			ready.countDown();
			if (!start.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("start latch was not released");
			}
			return client.listPublicRepositories();
		};
		List<Future<List<GithubRepository>>> futures = new ArrayList<>();
		for (int i = 0; i < CALLERS; i++) {
			futures.add(callerPool.submit(call));
		}
		assertTrue(ready.await(10, TimeUnit.SECONDS));
		start.countDown();
		List<GithubRepository> expected = futures.get(0).get(10, TimeUnit.SECONDS);
		for (int i = 1; i < futures.size(); i++) {
			assertEquals(expected, futures.get(i).get(10, TimeUnit.SECONDS));
		}
		server.verify(1, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}
}
