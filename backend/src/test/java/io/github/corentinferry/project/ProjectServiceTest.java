package io.github.corentinferry.project;

import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.github.model.GithubRepository;
import io.github.corentinferry.selection.RepositorySelectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

	private final GithubApiClient githubApiClient = mock(GithubApiClient.class);
	private final RepositorySelectionService selectionService = mock(RepositorySelectionService.class);

	private ProjectService service() {
		return new ProjectService(githubApiClient, selectionService);
	}

	@Test
	void returnsIntersectionOfSelectionAndGithubRepositories() {
		when(selectionService.list()).thenReturn(List.of(10L, 20L, 40L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(30L, "not-selected", "2026-12-01T00:00:00Z"),
				githubRepo(20L, "older", "2026-01-01T00:00:00Z"),
				githubRepo(10L, "newer", "2026-09-01T00:00:00Z")));

		assertEquals(List.of(
				project(10L, "newer", "2026-09-01T00:00:00Z"),
				project(20L, "older", "2026-01-01T00:00:00Z")), service().listProjects());
	}

	@Test
	void returnsEmptyListWithoutCallingGithubWhenSelectionIsEmpty() {
		when(selectionService.list()).thenReturn(List.of());

		assertEquals(List.of(), service().listProjects());
		verifyNoInteractions(githubApiClient);
	}

	@Test
	void ignoresSelectedIdAbsentFromGithub() {
		when(selectionService.list()).thenReturn(List.of(404L, 10L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(10L, "kept", "2026-02-01T00:00:00Z")));

		assertEquals(List.of(project(10L, "kept", "2026-02-01T00:00:00Z")), service().listProjects());
	}

	@Test
	void duplicateGithubIdsProduceOneProject() {
		when(selectionService.list()).thenReturn(List.of(1L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "first", "2026-02-01T00:00:00Z"),
				githubRepo(1L, "second", "2026-01-01T00:00:00Z")));

		assertEquals(List.of(project(1L, "first", "2026-02-01T00:00:00Z")), service().listProjects());
	}

	@Test
	void sortsByPushedAtDescendingWithNullLast() {
		when(selectionService.list()).thenReturn(List.of(1L, 2L, 3L, 4L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(2L, "old", "2026-01-01T00:00:00Z"),
				githubRepo(4L, "never-pushed", null),
				githubRepo(1L, "new", "2026-12-01T00:00:00Z"),
				githubRepo(3L, "middle", "2026-10-01T00:00:00Z")));

		assertEquals(List.of(
				project(1L, "new", "2026-12-01T00:00:00Z"),
				project(3L, "middle", "2026-10-01T00:00:00Z"),
				project(2L, "old", "2026-01-01T00:00:00Z"),
				project(4L, "never-pushed", null)), service().listProjects());
	}

	@Test
	void preservesGithubOrderForEqualPushedAt() {
		when(selectionService.list()).thenReturn(List.of(1L, 2L, 3L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(3L, "third-in-github", "2026-06-01T00:00:00Z"),
				githubRepo(1L, "first-in-github", "2026-06-01T00:00:00Z"),
				githubRepo(2L, "second-in-github", "2026-06-01T00:00:00Z")));

		assertEquals(List.of(
				project(3L, "third-in-github", "2026-06-01T00:00:00Z"),
				project(1L, "first-in-github", "2026-06-01T00:00:00Z"),
				project(2L, "second-in-github", "2026-06-01T00:00:00Z")), service().listProjects());
	}

	@ParameterizedTest
	@MethodSource("unsafeHomepages")
	void unsafeHomepagesBecomeNull(String homepage) {
		when(selectionService.list()).thenReturn(List.of(1L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "kept", "2026-01-01T00:00:00Z", "https://example.invalid/kept", homepage)));

		assertEquals(List.of(new ProjectResponse(1L, "kept", "description-1",
				"https://example.invalid/kept", null, "Java-1", "2026-01-01T00:00:00Z")),
				service().listProjects());
	}

	private static Stream<Arguments> unsafeHomepages() {
		return Stream.of(
				Arguments.of("javascript:alert(1)"),
				Arguments.of("http://example.invalid"),
				Arguments.of(""),
				Arguments.of("https://exa mple.invalid"),
				Arguments.of("https:"));
	}

	@Test
	void keepsUppercaseHttpsHomepageUnchanged() {
		when(selectionService.list()).thenReturn(List.of(1L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "kept", "2026-01-01T00:00:00Z",
						"https://example.invalid/kept", "HTTPS://example.invalid")));

		assertEquals(List.of(new ProjectResponse(1L, "kept", "description-1",
				"https://example.invalid/kept", "HTTPS://example.invalid", "Java-1",
				"2026-01-01T00:00:00Z")), service().listProjects());
	}

	@ParameterizedTest
	@MethodSource("unsafeHtmlUrls")
	void unsafeHtmlUrlExcludesRepositoryButKeepsOthers(String htmlUrl) {
		when(selectionService.list()).thenReturn(List.of(1L, 2L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "insecure", "2026-12-01T00:00:00Z", htmlUrl, "https://home-1.example"),
				githubRepo(2L, "secure", "2026-01-01T00:00:00Z")));

		assertEquals(List.of(project(2L, "secure", "2026-01-01T00:00:00Z")), service().listProjects());
	}

	private static Stream<Arguments> unsafeHtmlUrls() {
		return Stream.of(
				Arguments.of("http://example.invalid/insecure"),
				Arguments.of("https://exa mple.invalid/x"));
	}

	@Test
	void nullHtmlUrlExcludesRepositoryWithoutException() {
		when(selectionService.list()).thenReturn(List.of(1L, 2L));
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of(
				githubRepo(1L, "null-url", "2026-12-01T00:00:00Z", null, "https://home-1.example"),
				githubRepo(2L, "secure", "2026-01-01T00:00:00Z")));

		assertEquals(List.of(project(2L, "secure", "2026-01-01T00:00:00Z")), service().listProjects());
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

	private static ProjectResponse project(long id, String name, String pushedAt) {
		return new ProjectResponse(id, name, "description-" + id,
				"https://example.invalid/" + name, "https://home-" + id + ".example",
				"Java-" + id, pushedAt);
	}
}
