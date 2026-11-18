package io.github.corentinferry.github.client;

import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
	"github.token=test-token",
	"github.owner=testowner",
	"github.api-base-url=http://localhost:18080",
	"github.connect-timeout=1234",
	"github.read-timeout=4321"
})
class GithubApiClientWiringTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextExposesSingleGithubApiClientBean() {
		assertEquals(1, context.getBeansOfType(GithubApiClient.class).size());
		GithubApiClient client = context.getBean(GithubApiClient.class);
		assertInstanceOf(DefaultGithubApiClient.class, client);
	}

	@Test
	void clientReceivesValidatedConfiguration() {
		GithubApiClient client = context.getBean(GithubApiClient.class);
		GithubProperties properties = context.getBean(GithubProperties.class);

		assertSame(properties, ReflectionTestUtils.getField(client, "properties"));
		assertEquals("http://localhost:18080", properties.getApiBaseUrl());
		assertEquals(1234, properties.getConnectTimeout());
		assertEquals(4321, properties.getReadTimeout());
	}
}
