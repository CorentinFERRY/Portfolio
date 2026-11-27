package io.github.corentinferry.controller;

import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.selection.RepositorySelectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs in a real servlet container because MockMvc does not perform the error
 * dispatch to /error: only a real container shows what a client receives when
 * an exception has no dedicated handler, and whether /error stays private
 * when it is requested directly.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ErrorDispatchContainerTest {

	private static final String SENTINEL = "sentinel-internal-detail jdbc:h2:mem:secret";

	@LocalServerPort
	private int port;

	@MockitoBean
	private RepositorySelectionService selectionService;

	@MockitoBean
	private GithubApiClient githubApiClient;

	@Test
	void nonGithubFailureReturns500WithoutDetails() throws Exception {
		when(selectionService.list()).thenThrow(new IllegalStateException(SENTINEL));

		HttpResponse<String> response = send("/api/projects");

		assertEquals(500, response.statusCode(), "body: " + response.body());
		String body = response.body();
		for (String forbidden : List.of("sentinel-internal-detail", "jdbc:h2", "IllegalStateException",
				"\"trace\"", "\"exception\"", "\"message\"")) {
			assertFalse(body.contains(forbidden), "response body leaked " + forbidden + ": " + body);
		}
		verifyNoInteractions(githubApiClient);
	}

	@Test
	void directErrorRequestIsForbidden() throws Exception {
		HttpResponse<String> response = send("/error");

		assertEquals(403, response.statusCode(), "body: " + response.body());
	}

	private HttpResponse<String> send(String path) throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
					.GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}
}
