package io.github.corentinferry.config;

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
import static org.mockito.Mockito.when;

/**
 * Proves the security headers on responses written by a real servlet
 * container, including the error dispatch that MockMvc does not perform.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SecurityHeadersContainerTest {

	private static final String API_CSP =
			"default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	@LocalServerPort
	private int port;

	@MockitoBean
	private RepositorySelectionService selectionService;

	@MockitoBean
	private GithubApiClient githubApiClient;

	@Test
	void unexpectedFailureCarriesSecurityHeaders() throws Exception {
		when(selectionService.list()).thenThrow(new IllegalStateException("unexpected"));

		HttpResponse<String> response = send("/api/projects");

		// Precondition: the headers must be checked on the 500 itself.
		assertEquals(500, response.statusCode(), "body: " + response.body());
		assertSecurityHeaders(response);
	}

	@Test
	void forbiddenRouteCarriesSecurityHeaders() throws Exception {
		HttpResponse<String> response = send("/api/unknown");

		assertEquals(403, response.statusCode(), "body: " + response.body());
		assertSecurityHeaders(response);
	}

	private HttpResponse<String> send(String path) throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
					.GET()
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString());
		}
	}

	private static void assertSecurityHeaders(HttpResponse<String> response) {
		// allValues also rejects a duplicated header, which a filter applied twice would produce.
		assertEquals(List.of(API_CSP), response.headers().allValues("Content-Security-Policy"));
		assertEquals(List.of("no-referrer"), response.headers().allValues("Referrer-Policy"));
		assertEquals(List.of("nosniff"), response.headers().allValues("X-Content-Type-Options"));
		assertEquals(List.of("DENY"), response.headers().allValues("X-Frame-Options"));
	}
}
