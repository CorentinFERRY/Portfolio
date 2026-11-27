package io.github.corentinferry.config;

import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.selection.RepositorySelectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the CORS policy for a configured allow list: listed origins may read
 * GET responses, every other cross-origin request is rejected by the CORS
 * filter itself, and the security headers stay on the rejections.
 *
 * <p>MockMvc serves {@code http://localhost:80}, so no origin used here is
 * same-origin: a same-origin request would not be a CORS request at all.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "portfolio.cors.allowed-origins=https://portfolio.example,http://localhost:5173")
@AutoConfigureMockMvc
class CorsConfigTest {

	private static final String ALLOWED_ORIGIN = "https://portfolio.example";
	private static final String UNLISTED_ORIGIN = "https://unlisted.example";
	private static final String CORS_REJECTION_BODY = "Invalid CORS request";
	private static final String API_CSP =
			"default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RepositorySelectionRepository selectionRepository;

	@MockitoBean
	private GithubApiClient githubApiClient;

	@BeforeEach
	void emptySelection() {
		selectionRepository.deleteAll();
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of());
	}

	@Test
	void allowedOriginGetEchoesThatOriginWithoutCredentials() throws Exception {
		mockMvc.perform(get("/api/projects").header("Origin", ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				// The exact echoed value also proves the response is never "*".
				.andExpect(header().stringValues("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
				.andExpect(header().stringValues("Vary", hasItem("Origin")))
				.andExpectAll(securityHeaders());
	}

	@Test
	void allowedOriginPreflightForGetAllowsGetOnly() throws Exception {
		mockMvc.perform(options("/api/projects")
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().stringValues("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
				.andExpect(header().string("Access-Control-Allow-Methods", "GET"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
				.andExpectAll(securityHeaders());
	}

	@Test
	void unlistedOriginGetIsRejectedByCors() throws Exception {
		mockMvc.perform(get("/api/projects").header("Origin", UNLISTED_ORIGIN))
				.andExpect(status().isForbidden())
				// The body tells a CORS rejection apart from the deny-by-default 403.
				.andExpect(content().string(CORS_REJECTION_BODY))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
				.andExpectAll(securityHeaders());
	}

	@Test
	void allowedOriginPreflightForPostIsRejectedByCors() throws Exception {
		mockMvc.perform(options("/api/projects")
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(CORS_REJECTION_BODY))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
				.andExpectAll(securityHeaders());
	}

	@Test
	void requestWithoutOriginIsNotTreatedAsCors() throws Exception {
		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Methods"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
	}

	@Test
	void unlistedOriginPreflightForGetIsRejectedByCors() throws Exception {
		mockMvc.perform(options("/api/projects")
						.header("Origin", UNLISTED_ORIGIN)
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(CORS_REJECTION_BODY))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
				.andExpectAll(securityHeaders());
	}

	private static ResultMatcher[] securityHeaders() {
		return new ResultMatcher[] {
				header().string("Content-Security-Policy", API_CSP),
				header().string("Referrer-Policy", "no-referrer"),
				header().string("X-Content-Type-Options", "nosniff"),
				header().string("X-Frame-Options", "DENY")};
	}
}
