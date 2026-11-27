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
 * Proves, in a real servlet container, that an empty allow list still
 * registers a CORS policy: every cross-origin request gets an explicit 403
 * that carries the security headers, while plain requests are unaffected.
 *
 * <p>The property is forced empty so an exported environment variable cannot
 * change the result; the application.yml default is proven by
 * {@link CorsPropertiesTest}.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = "portfolio.cors.allowed-origins=")
class CorsDefaultConfigTest {

	private static final String API_CSP =
			"default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	@LocalServerPort
	private int port;

	@MockitoBean
	private RepositorySelectionService selectionService;

	@MockitoBean
	private GithubApiClient githubApiClient;

	@Test
	void crossOriginGetIsRejectedWithSecurityHeaders() throws Exception {
		when(selectionService.list()).thenReturn(List.of());

		HttpResponse<String> response = send("/api/projects", "http://localhost:5173");

		assertEquals(403, response.statusCode(), "body: " + response.body());
		assertEquals("Invalid CORS request", response.body());
		assertEquals(List.of(), response.headers().allValues("Access-Control-Allow-Origin"));
		assertSecurityHeaders(response);
	}

	@Test
	void requestWithoutOriginIsNotTreatedAsCors() throws Exception {
		HttpResponse<String> response = send("/api/health", null);

		assertEquals(200, response.statusCode(), "body: " + response.body());
		assertEquals(List.of(), response.headers().allValues("Access-Control-Allow-Origin"));
	}

	private HttpResponse<String> send(String path, String origin) throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
					.GET();
			if (origin != null) {
				request.header("Origin", origin);
			}
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
