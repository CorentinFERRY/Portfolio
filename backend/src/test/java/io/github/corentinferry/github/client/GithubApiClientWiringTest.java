package io.github.corentinferry.github.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = {
	"github.token=test-token",
	"github.owner=testowner",
	"github.api-base-url=http://localhost:18080",
	"github.connect-timeout=1234",
	"github.read-timeout=4321",
	"github.cache-ttl-ms=12345",
	"github.cache-maximum-size=7"
})
class GithubApiClientWiringTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextExposesSingleGithubApiClientBean() {
		assertEquals(1, context.getBeansOfType(GithubApiClient.class).size());
		assertEquals(0, context.getBeansOfType(DefaultGithubApiClient.class).size());
		GithubApiClient client = context.getBean(GithubApiClient.class);
		assertInstanceOf(CachingGithubApiClient.class, client);
	}

	@Test
	void clientReceivesValidatedConfiguration() {
		CachingGithubApiClient client = context.getBean(CachingGithubApiClient.class);
		GithubProperties properties = context.getBean(GithubProperties.class);
		GithubApiClient delegate = (GithubApiClient) ReflectionTestUtils.getField(client, "delegate");

		assertInstanceOf(DefaultGithubApiClient.class, delegate);
		assertSame(properties, ReflectionTestUtils.getField(delegate, "properties"));
		assertEquals("http://localhost:18080", properties.getApiBaseUrl());
		assertEquals(1234, properties.getConnectTimeout());
		assertEquals(4321, properties.getReadTimeout());
	}

	@Test
	void cacheReceivesValidatedConfiguration() {
		CachingGithubApiClient client = context.getBean(CachingGithubApiClient.class);
		GithubProperties properties = context.getBean(GithubProperties.class);
		Cache<Object, ?> cache = (Cache<Object, ?>) ReflectionTestUtils.getField(client, "cache");

		assertSame(properties, ReflectionTestUtils.getField(client, "properties"));
		assertNotNull(cache);
		Policy<Object, ?> policy = cache.policy();
		assertEquals(7L, policy.eviction().orElseThrow().getMaximum());
		assertEquals(Duration.ofMillis(12345), policy.expireAfterWrite().orElseThrow().getExpiresAfter());
	}
}
