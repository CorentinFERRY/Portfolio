package io.github.corentinferry.config;

import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import io.github.corentinferry.github.exception.GithubUnexpectedResponseException;
import io.github.corentinferry.selection.RepositorySelection;
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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that every API response, successful or not, carries the security
 * headers: a browser may render any of them, including error responses.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {

	private static final String API_CSP =
			"default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RepositorySelectionRepository selectionRepository;

	@MockitoBean
	private GithubApiClient githubApiClient;

	@BeforeEach
	void clearSelection() {
		selectionRepository.deleteAll();
	}

	@Test
	void healthResponseCarriesSecurityHeaders() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpectAll(securityHeaders());
	}

	@Test
	void projectsResponseCarriesSecurityHeaders() throws Exception {
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of());

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpectAll(securityHeaders());
	}

	@Test
	void forbiddenUnknownRouteCarriesSecurityHeaders() throws Exception {
		mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isForbidden())
				.andExpectAll(securityHeaders());
	}

	@Test
	void forbiddenPostHealthCarriesSecurityHeaders() throws Exception {
		// The CSRF token lets the request reach the authorization rules, so the
		// 403 comes from the deny-by-default rule and not from the CSRF filter.
		mockMvc.perform(post("/api/health").with(csrf()))
				.andExpect(status().isForbidden())
				.andExpectAll(securityHeaders());
	}

	@Test
	void githubUnavailableCarriesSecurityHeaders() throws Exception {
		selectionRepository.save(new RepositorySelection(1L));
		when(githubApiClient.listPublicRepositories())
				.thenThrow(new GithubUnavailableException("unavailable"));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isServiceUnavailable())
				.andExpectAll(securityHeaders());
	}

	@Test
	void githubErrorCarriesSecurityHeaders() throws Exception {
		selectionRepository.save(new RepositorySelection(1L));
		when(githubApiClient.listPublicRepositories())
				.thenThrow(new GithubUnexpectedResponseException("unexpected"));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isBadGateway())
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
