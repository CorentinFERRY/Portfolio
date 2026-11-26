package io.github.corentinferry.config;

import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.selection.RepositorySelectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ProjectsSecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RepositorySelectionRepository selectionRepository;

	@MockitoBean
	private GithubApiClient githubApiClient;

	@BeforeEach
	void clearSelection() {
		selectionRepository.deleteAll();
		when(githubApiClient.listPublicRepositories()).thenReturn(List.of());
	}

	@Test
	void getProjectsWithoutAuthenticationIsAllowed() throws Exception {
		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@ParameterizedTest
	@MethodSource("mutatingMethods")
	void mutatingProjectsRequestsWithoutAuthenticationAreForbidden(HttpMethod method) throws Exception {
		mockMvc.perform(request(method, "/api/projects").with(csrf()))
				.andExpect(status().isForbidden());
	}

	private static Stream<Arguments> mutatingMethods() {
		return Stream.of(
				Arguments.of(HttpMethod.POST),
				Arguments.of(HttpMethod.PUT),
				Arguments.of(HttpMethod.DELETE));
	}
}
