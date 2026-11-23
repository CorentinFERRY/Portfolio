package io.github.corentinferry.github.client;

import com.github.benmanes.caffeine.cache.Ticker;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubAccessDeniedException;
import io.github.corentinferry.github.exception.GithubAuthenticationFailureException;
import io.github.corentinferry.github.exception.GithubOwnerNotFoundException;
import io.github.corentinferry.github.exception.GithubRateLimitExceededException;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CachingGithubApiClientErrorTest {

	private static final String REPOS_PATH = "/users/fictional-owner/repos";

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

	private GithubApiClient cachingClient(GithubProperties properties) {
		return new CachingGithubApiClient(new DefaultGithubApiClient(properties), properties,
				Ticker.systemTicker(), Runnable::run);
	}

	private static Stream<Arguments> failures() {
		return Stream.of(
				Arguments.of("401", 401, GithubAuthenticationFailureException.class),
				Arguments.of("403", 403, GithubAccessDeniedException.class),
				Arguments.of("404", 404, GithubOwnerNotFoundException.class),
				Arguments.of("429", 429, GithubRateLimitExceededException.class),
				Arguments.of("500", 500, GithubUnavailableException.class),
				Arguments.of("read timeout", 0, GithubUnavailableException.class));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("failures")
	void failuresAreRetriedOnNextCall(String name, int status, Class<? extends RuntimeException> expected) {
		if (status == 0) {
			server.stubFor(get(urlPathEqualTo(REPOS_PATH))
					.willReturn(aResponse().withStatus(200).withFixedDelay(2000).withBody("[]")));
		} else {
			server.stubFor(get(urlPathEqualTo(REPOS_PATH))
					.willReturn(aResponse().withStatus(status)));
		}
		GithubProperties properties = status == 0
				? new GithubProperties(props.getToken(), props.getOwner(), props.getApiBaseUrl(), 3000, 200)
				: props;
		GithubApiClient client = cachingClient(properties);

		assertEquals(expected, assertThrows(expected, client::listPublicRepositories).getClass());
		assertEquals(expected, assertThrows(expected, client::listPublicRepositories).getClass());
		server.verify(2, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}
}
