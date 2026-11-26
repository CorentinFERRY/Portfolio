package io.github.corentinferry.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.corentinferry.selection.RepositorySelection;
import io.github.corentinferry.selection.RepositorySelectionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectsEndpointWireMockTest {

	private static final String REPOS_PATH = "/users/testowner/repos";
	private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

	static {
		SERVER.start();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RepositorySelectionRepository selectionRepository;

	@DynamicPropertySource
	static void configureGithubProperties(DynamicPropertyRegistry registry) {
		registry.add("github.api-base-url", () -> "http://localhost:" + SERVER.port());
		registry.add("github.owner", () -> "testowner");
		registry.add("github.token", () -> "test-token");
		registry.add("github.cache-ttl-ms", () -> "300000");
		registry.add("github.cache-maximum-size", () -> "100");
	}

	@BeforeAll
	static void startServer() {
		if (!SERVER.isRunning()) {
			SERVER.start();
		}
	}

	@AfterAll
	static void stopServer() {
		SERVER.stop();
	}

	@BeforeEach
	void resetState() {
		SERVER.resetAll();
		selectionRepository.deleteAll();
	}

	@Test
	void selectedPrivateRepositoryIsNotExposed() throws Exception {
		selectionRepository.save(new RepositorySelection(77L));
		SERVER.stubFor(WireMock.get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("[{\"id\":77,\"name\":\"secret\",\"private\":true,"
								+ "\"html_url\":\"https://example.invalid/secret\","
								+ "\"stargazers_count\":0,\"fork\":false,\"archived\":false}]")));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").doesNotExist());

		SERVER.verify(1, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}

	@Test
	void consecutiveGetProjectsRequestsUseCachedGithubClient() throws Exception {
		selectionRepository.save(new RepositorySelection(88L));
		SERVER.stubFor(WireMock.get(urlPathEqualTo(REPOS_PATH))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("[{\"id\":88,\"name\":\"cached\",\"private\":false,"
								+ "\"html_url\":\"https://example.invalid/cached\","
								+ "\"stargazers_count\":0,\"fork\":false,\"archived\":false}]")));

		mockMvc.perform(get("/api/projects")).andExpect(status().isOk());
		mockMvc.perform(get("/api/projects")).andExpect(status().isOk());

		SERVER.verify(1, getRequestedFor(urlPathEqualTo(REPOS_PATH)));
	}
}
