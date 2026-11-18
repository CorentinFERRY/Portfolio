package io.github.corentinferry.github.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubPaginationViolationException;
import io.github.corentinferry.github.exception.GithubUnexpectedResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubApiClientSecondServerTest {

	private static final String TOKEN = "fake-cross-origin-token";
	private static final String REPOS_PATH = "/users/fictional-owner/repos";

	private WireMockServer first;
	private WireMockServer second;
	private GithubProperties props;

	@BeforeEach
	void setUp() {
		first = new WireMockServer(wireMockConfig().dynamicPort());
		second = new WireMockServer(wireMockConfig().dynamicPort());
		first.start();
		second.start();
		props = new GithubProperties(TOKEN, "fictional-owner", "http://localhost:" + first.port(), 3000, 5000);
	}

	@AfterEach
	void tearDown() {
		if (second != null) {
			second.stop();
		}
		if (first != null) {
			first.stop();
		}
	}

	@Test
	void nextLinkToSecondServerIsRejectedWithoutRequest() {
		first.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Link", "<http://localhost:" + second.port() + REPOS_PATH + "?page=2>; rel=\"next\"")
						.withBody(RepositoryResponses.singlePublicRepositoryPage())));
		second.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(200)
						.withBody(RepositoryResponses.singlePublicRepositoryPage())));

		GithubApiClient client = new DefaultGithubApiClient(props);

		assertAll(
				() -> {
					GithubPaginationViolationException ex = assertThrows(GithubPaginationViolationException.class,
							client::listPublicRepositories);
					assertFalse(ex.getMessage().contains(TOKEN), "exception message contains the token");
				},
				this::assertFirstServerReceivedToken,
				this::assertSecondServerReceivedNothing
		);
	}

	@Test
	void redirectToSecondServerIsRejectedWithoutRequest() {
		first.stubFor(get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(302)
						.withHeader("Location", "http://localhost:" + second.port() + "/redirect-target")));
		second.stubFor(get(urlPathEqualTo("/redirect-target"))
				.willReturn(aResponse().withStatus(200)
						.withBody(RepositoryResponses.singlePublicRepositoryPage())));

		GithubApiClient client = new DefaultGithubApiClient(props);

		assertAll(
				() -> {
					GithubUnexpectedResponseException ex = assertThrows(GithubUnexpectedResponseException.class,
							client::listPublicRepositories);
					assertFalse(ex.getMessage().contains(TOKEN), "exception message contains the token");
				},
				this::assertFirstServerReceivedToken,
				this::assertSecondServerReceivedNothing
		);
	}

	private void assertFirstServerReceivedToken() {
		var events = first.getAllServeEvents();
		assertEquals(1, events.size(), "first server did not receive exactly one request");
		if (events.size() != 1) {
			return;
		}
		var request = events.get(0).getRequest();
		assertEquals("GET", request.getMethod().getName(), "first server method");
		assertTrue(request.getUrl().startsWith(REPOS_PATH + "?"), "first server path");
		assertTrue(("Bearer " + TOKEN).equals(request.getHeader("Authorization")),
				"first server did not receive the fake token");
	}

	private void assertSecondServerReceivedNothing() {
		assertEquals(0, second.findAll(anyRequestedFor(anyUrl())).size(), "second server received requests");
		assertTrue(second.getAllServeEvents().isEmpty(), "second server served events");
	}
}
