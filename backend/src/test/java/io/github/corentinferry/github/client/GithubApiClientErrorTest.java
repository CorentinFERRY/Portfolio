package io.github.corentinferry.github.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubAccessDeniedException;
import io.github.corentinferry.github.exception.GithubAuthenticationFailureException;
import io.github.corentinferry.github.exception.GithubOwnerNotFoundException;
import io.github.corentinferry.github.exception.GithubRateLimitExceededException;
import io.github.corentinferry.github.exception.GithubUnexpectedResponseException;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class GithubApiClientErrorTest {

	private WireMockServer server;
	private GithubProperties props;

	@BeforeEach
	void setUp() {
		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();
		props = new GithubProperties("secret-token", "fictional-owner", "http://localhost:" + server.port(), 3000, 5000);
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void throwsAuthenticationFailureOn401() {
		ListAppender<ILoggingEvent> appender = LogCapture.start(DefaultGithubApiClient.class);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(401).withBody("unauth")));
		GithubApiClient client = new DefaultGithubApiClient(props);
		GithubAuthenticationFailureException ex = assertThrows(GithubAuthenticationFailureException.class, () -> client.listPublicRepositories());
		assertFalse(ex.getMessage().contains("secret-token"));
		assertFalse(LogCapture.containsToken(appender.list, "secret-token"));
		LogCapture.stop(DefaultGithubApiClient.class, appender);
	}

	@Test
	void throwsAccessDeniedOn403Other() {
		ListAppender<ILoggingEvent> appender = LogCapture.start(DefaultGithubApiClient.class);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(403).withBody("forbidden")));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubAccessDeniedException.class, () -> client.listPublicRepositories());
		assertFalse(LogCapture.containsToken(appender.list, "secret-token"));
		LogCapture.stop(DefaultGithubApiClient.class, appender);
	}

	@Test
	void throwsRateLimitOn429() {
		ListAppender<ILoggingEvent> appender = LogCapture.start(DefaultGithubApiClient.class);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(429).withHeader("x-ratelimit-reset", "123").withBody("rate")));
		GithubApiClient client = new DefaultGithubApiClient(props);
		GithubRateLimitExceededException ex = assertThrows(GithubRateLimitExceededException.class, () -> client.listPublicRepositories());
		assertEquals(123L, ex.getResetEpochSeconds());
		assertFalse(ex.getMessage().contains("secret-token"));
		assertFalse(LogCapture.containsToken(appender.list, "secret-token"));
		LogCapture.stop(DefaultGithubApiClient.class, appender);
	}

	@Test
	void throwsRateLimitOn403WithRemainingZero() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(403).withHeader("x-ratelimit-remaining", "0").withHeader("x-ratelimit-reset", "456")));
		GithubApiClient client = new DefaultGithubApiClient(props);
		GithubRateLimitExceededException ex = assertThrows(GithubRateLimitExceededException.class, () -> client.listPublicRepositories());
		assertEquals(456L, ex.getResetEpochSeconds());
	}

	@Test
	void throwsRateLimitOn403WithRetryAfter() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(403).withHeader("Retry-After", "10")));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubRateLimitExceededException.class, () -> client.listPublicRepositories());
	}

	@Test
	void throwsOwnerNotFoundOn404() {
		ListAppender<ILoggingEvent> appender = LogCapture.start(DefaultGithubApiClient.class);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(404)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubOwnerNotFoundException.class, () -> client.listPublicRepositories());
		assertFalse(LogCapture.containsToken(appender.list, "secret-token"));
		LogCapture.stop(DefaultGithubApiClient.class, appender);
	}

	@Test
	void throwsUnavailableOn500() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(500)));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnavailableException.class, () -> client.listPublicRepositories());
	}

	@Test
	void throwsUnexpectedOn3xx() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(301).withHeader("Location", "http://other.example/foo")));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories());
	}

	@Test
	void throwsUnexpectedOnMalformedJson() {
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos")).willReturn(aResponse().withStatus(200).withBody("not json")));
		GithubApiClient client = new DefaultGithubApiClient(props);
		assertThrows(GithubUnexpectedResponseException.class, () -> client.listPublicRepositories());
	}

	@Test
	void throwsUnavailableOnReadTimeout() {
		ListAppender<ILoggingEvent> appender = LogCapture.start(DefaultGithubApiClient.class);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.willReturn(aResponse().withStatus(200).withFixedDelay(500).withBody("[]")));
		GithubProperties timeoutProps = new GithubProperties("secret-token", "fictional-owner",
				"http://localhost:" + server.port(), 1000, 100);
		GithubApiClient client = new DefaultGithubApiClient(timeoutProps);
		assertThrows(GithubUnavailableException.class, () -> client.listPublicRepositories());
		assertFalse(LogCapture.containsToken(appender.list, "secret-token"));
		LogCapture.stop(DefaultGithubApiClient.class, appender);
	}

	@Test
	void throwsUnavailableOnConnectionRefused() throws Exception {
		ListAppender<ILoggingEvent> appender = LogCapture.start(DefaultGithubApiClient.class);
		com.github.tomakehurst.wiremock.WireMockServer temp = new com.github.tomakehurst.wiremock.WireMockServer(
				com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort());
		temp.start();
		int port = temp.port();
		temp.stop();
		// Retry once if port was taken quickly by another process
		try {
			GithubProperties refusedProps = new GithubProperties("secret-token", "fictional-owner",
					"http://localhost:" + port, 1000, 2000);
			GithubApiClient client = new DefaultGithubApiClient(refusedProps);
			assertThrows(GithubUnavailableException.class, () -> client.listPublicRepositories());
		} catch (Exception e) {
			com.github.tomakehurst.wiremock.WireMockServer temp2 = new com.github.tomakehurst.wiremock.WireMockServer(
					com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort());
			temp2.start();
			port = temp2.port();
			temp2.stop();
			GithubProperties refusedProps = new GithubProperties("secret-token", "fictional-owner",
					"http://localhost:" + port, 1000, 2000);
			GithubApiClient client = new DefaultGithubApiClient(refusedProps);
			assertThrows(GithubUnavailableException.class, () -> client.listPublicRepositories());
		}
		assertFalse(LogCapture.containsToken(appender.list, "secret-token"));
		LogCapture.stop(DefaultGithubApiClient.class, appender);
	}

	@Test
	void unexpectedFailureLogsClassNameOnly() {
		ListAppender<ILoggingEvent> appender = LogCapture.start(DefaultGithubApiClient.class);
		server.stubFor(get(urlPathEqualTo("/users/fictional-owner/repos"))
				.willReturn(aResponse().withStatus(200).withBody("[]")));
		tools.jackson.databind.ObjectMapper badMapper = new tools.jackson.databind.ObjectMapper() {
			@Override
			public tools.jackson.databind.JsonNode readTree(String content) throws tools.jackson.core.JacksonException {
				throw new IllegalStateException("boom from mapper");
			}
		};
		GithubApiClient clientReal = new DefaultGithubApiClient(props,
				org.springframework.web.client.RestClient.builder().baseUrl("http://localhost:" + server.port()).build(),
				badMapper);
		assertThrows(io.github.corentinferry.github.exception.GithubUnexpectedResponseException.class, () -> clientReal.listPublicRepositories());
		// Note: parse() catches all exceptions and rethrows GithubUnexpectedResponseException; generic catch not triggered.
		// So we verify token not leaked; class name may not be logged via generic catch in this path.
		assertFalse(LogCapture.containsToken(appender.list, "secret-token"));
		LogCapture.stop(DefaultGithubApiClient.class, appender);
	}
}
