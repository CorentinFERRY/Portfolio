package io.github.corentinferry.controller;

import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.github.exception.GithubAccessDeniedException;
import io.github.corentinferry.github.exception.GithubAuthenticationFailureException;
import io.github.corentinferry.github.exception.GithubOwnerNotFoundException;
import io.github.corentinferry.github.exception.GithubPaginationViolationException;
import io.github.corentinferry.github.exception.GithubRateLimitExceededException;
import io.github.corentinferry.github.exception.GithubUnexpectedResponseException;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import io.github.corentinferry.github.model.GithubRepository;
import io.github.corentinferry.selection.RepositorySelection;
import io.github.corentinferry.selection.RepositorySelectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ProjectsEndpointTest {

	private static final String LEAK_MESSAGE =
			"sentinel-upstream-detail https://internal.invalid/v1 token=test-token";

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
	void getProjectsWithoutAuthenticationReturns200WithOnlyDtoFields() throws Exception {
		select(1L);
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "r1", "2026-10-01T00:00:00Z")));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].name").value("r1"))
				.andExpect(jsonPath("$[0].description").value("description-1"))
				.andExpect(jsonPath("$[0].htmlUrl").value("https://example.invalid/r1"))
				.andExpect(jsonPath("$[0].homepage").value("https://home-1.example"))
				.andExpect(jsonPath("$[0].language").value("Java-1"))
				.andExpect(jsonPath("$[0].pushedAt").value("2026-10-01T00:00:00Z"))
				.andExpect(jsonPath("$[0].*", hasSize(7)));
	}

	@Test
	void getProjectsReturnsEmptyJsonArrayWhenNothingIsSelected() throws Exception {
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "not-selected", "2026-10-01T00:00:00Z")));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		verifyNoInteractions(githubApiClient);
	}

	@Test
	void getProjectsIgnoresSelectedIdMissingFromGithub() throws Exception {
		select(404L, 1L);
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "kept", "2026-02-01T00:00:00Z")));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(1));
	}

	@Test
	void getProjectsSortsByPushedAtDescending() throws Exception {
		select(1L, 2L, 3L, 4L);
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(2L, "old", "2026-01-01T00:00:00Z"),
				githubRepo(4L, "never-pushed", null),
				githubRepo(1L, "new", "2026-12-01T00:00:00Z"),
				githubRepo(3L, "middle", "2026-10-01T00:00:00Z")));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[1].id").value(3))
				.andExpect(jsonPath("$[2].id").value(2))
				.andExpect(jsonPath("$[3].id").value(4));
	}

	@Test
	void nonHttpsHomepagesAreSerializedAsNull() throws Exception {
		select(1L, 2L);
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "script", "2026-02-01T00:00:00Z",
						"https://example.invalid/script", "javascript:alert(1)"),
				githubRepo(2L, "plain-http", "2026-01-01T00:00:00Z",
						"https://example.invalid/plain-http", "http://example.invalid")));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].homepage").value(nullValue()))
				.andExpect(jsonPath("$[1].homepage").value(nullValue()))
				.andExpect(jsonPath("$[0].htmlUrl").value("https://example.invalid/script"))
				.andExpect(jsonPath("$[1].htmlUrl").value("https://example.invalid/plain-http"));
	}

	@Test
	void repositoryWithoutHttpsHtmlUrlIsAbsent() throws Exception {
		select(1L);
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "insecure", "2026-01-01T00:00:00Z",
						"http://example.invalid/insecure", "https://home-1.example")));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void nullFieldsAreSerializedAsJsonNull() throws Exception {
		select(1L);
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				new GithubRepository(1L, "nulls", null, "https://example.invalid/nulls",
						null, null, List.of(), 0, false, false, null)));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("nulls"))
				.andExpect(jsonPath("$[0].description").value(nullValue()))
				.andExpect(jsonPath("$[0].homepage").value(nullValue()))
				.andExpect(jsonPath("$[0].language").value(nullValue()))
				.andExpect(jsonPath("$[0].pushedAt").value(nullValue()));
	}

	@Test
	void githubUnavailableReturns503WithoutInternalDetails() throws Exception {
		select(1L);
		when(githubApiClient.listPublicRepositories())
				.thenThrow(new GithubUnavailableException(LEAK_MESSAGE));

		MvcResult result = mockMvc.perform(get("/api/projects"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"error\":\"github_unavailable\"}"))
				.andReturn();

		assertNoInternalDetails(result, GithubUnavailableException.class.getSimpleName());
	}

	@Test
	void githubRateLimitReturns503WithoutInternalDetails() throws Exception {
		select(1L);
		when(githubApiClient.listPublicRepositories())
				.thenThrow(new GithubRateLimitExceededException(LEAK_MESSAGE, 123L));

		MvcResult result = mockMvc.perform(get("/api/projects"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"error\":\"github_unavailable\"}"))
				.andReturn();

		assertNoInternalDetails(result, GithubRateLimitExceededException.class.getSimpleName());
	}

	@ParameterizedTest
	@MethodSource("otherGithubFailures")
	void otherGithubFailuresReturn502WithoutInternalDetails(RuntimeException failure) throws Exception {
		select(1L);
		when(githubApiClient.listPublicRepositories()).thenThrow(failure);

		MvcResult result = mockMvc.perform(get("/api/projects"))
				.andExpect(status().isBadGateway())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"error\":\"github_error\"}"))
				.andReturn();

		assertNoInternalDetails(result, failure.getClass().getSimpleName());
	}

	private static Stream<Arguments> otherGithubFailures() {
		return Stream.of(
				Arguments.of(new GithubAuthenticationFailureException(LEAK_MESSAGE)),
				Arguments.of(new GithubAccessDeniedException(LEAK_MESSAGE)),
				Arguments.of(new GithubOwnerNotFoundException(LEAK_MESSAGE)),
				Arguments.of(new GithubUnexpectedResponseException(LEAK_MESSAGE)),
				Arguments.of(new GithubPaginationViolationException(LEAK_MESSAGE)));
	}

	private void select(long... ids) {
		for (long id : ids) {
			selectionRepository.save(new RepositorySelection(id));
		}
	}

	private static void assertNoInternalDetails(MvcResult result, String... extraForbidden) throws Exception {
		String body = result.getResponse().getContentAsString();
		List<String> forbidden = List.of(
				"sentinel-upstream-detail", "internal.invalid", "test-token", "Authorization");
		for (String value : forbidden) {
			assertFalse(body.contains(value), "response body leaked " + value + ": " + body);
		}
		for (String value : extraForbidden) {
			assertFalse(body.contains(value), "response body leaked " + value + ": " + body);
		}
		assertNull(result.getResponse().getHeader("Retry-After"));
	}

	private static GithubRepository githubRepo(long id, String name, String pushedAt) {
		return githubRepo(id, name, pushedAt, "https://example.invalid/" + name,
				"https://home-" + id + ".example");
	}

	private static GithubRepository githubRepo(long id, String name, String pushedAt,
			String htmlUrl, String homepage) {
		return new GithubRepository(id, name, "description-" + id, htmlUrl, homepage, "Java-" + id,
				List.of("ignored"), 5, false, false, pushedAt);
	}
}
