package io.github.corentinferry.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getUnknownRouteWithoutAuthenticationIsRejected() throws Exception {
		mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectedRequestDoesNotCreateSession() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isForbidden())
				.andReturn();

		assertNull(result.getRequest().getSession(false),
				"an anonymous rejected request must not allocate an HTTP session");
	}

	@Test
	void postHealthWithoutAuthenticationIsRejected() throws Exception {
		// The request carries a valid CSRF token so it reaches the authorization
		// rules: without it the CSRF filter would reject it first and the test
		// would not prove that only GET is allowed on /api/health.
		mockMvc.perform(post("/api/health").with(csrf()))
				.andExpect(status().isForbidden());
	}

}
