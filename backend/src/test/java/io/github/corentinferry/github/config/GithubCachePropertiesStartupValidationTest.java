package io.github.corentinferry.github.config;

import io.github.corentinferry.config.GithubPropertiesConfig;
import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GithubCachePropertiesStartupValidationTest {

	private static final String VALID_OWNER = "valid-owner";
	private static final String TOKEN_SENTINEL = "sentinel-token-7f3c9a";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(GithubPropertiesConfig.class)
			.withInitializer(context -> {
				context.getEnvironment().getPropertySources()
						.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
				context.getEnvironment().getPropertySources()
						.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
			});

	@Test
	void invalidCacheTtlStopsStartup() {
		assertStartupFailsWith("github.cache-ttl-ms must be positive",
				"github.owner=" + VALID_OWNER,
				"github.token=" + TOKEN_SENTINEL,
				"github.cache-ttl-ms=0");
		assertStartupFailsWith("github.cache-ttl-ms must be positive",
				"github.owner=" + VALID_OWNER,
				"github.token=" + TOKEN_SENTINEL,
				"github.cache-ttl-ms=-1");
	}

	@Test
	void invalidCacheMaximumSizeStopsStartup() {
		assertStartupFailsWith("github.cache-maximum-size must be positive",
				"github.owner=" + VALID_OWNER,
				"github.token=" + TOKEN_SENTINEL,
				"github.cache-maximum-size=0");
		assertStartupFailsWith("github.cache-maximum-size must be positive",
				"github.owner=" + VALID_OWNER,
				"github.token=" + TOKEN_SENTINEL,
				"github.cache-maximum-size=-1");
	}

	@Test
	void validCachePropertiesBind() {
		runner.withPropertyValues(
						"github.owner=" + VALID_OWNER,
						"github.token=" + TOKEN_SENTINEL,
						"github.cache-ttl-ms=45000",
						"github.cache-maximum-size=7")
				.run(context -> {
					assertNull(context.getStartupFailure(), "context should have started");
					GithubProperties properties = context.getBean(GithubProperties.class);
					assertEquals(45000, ReflectionTestUtils.getField(properties, "cacheTtlMs"));
					assertEquals(7, ReflectionTestUtils.getField(properties, "cacheMaximumSize"));
				});
	}

	@Test
	void applicationYmlDeclaresCacheDefaults() {
		ApplicationContextRunner ymlRunner = new ApplicationContextRunner()
				.withInitializer(context -> {
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					ConfigDataEnvironmentPostProcessor.applyTo(context.getEnvironment());
				});
		ymlRunner.run(context -> {
			assertEquals("300000", context.getEnvironment().getProperty("github.cache-ttl-ms"));
			assertEquals("100", context.getEnvironment().getProperty("github.cache-maximum-size"));
		});
	}

	@Test
	void envVarGITHUB_CACHE_TTL_MSBoundViaSystemEnvironmentSource() {
		Map<String, Object> env = new HashMap<>();
		env.put("GITHUB_CACHE_TTL_MS", "60000");
		envRunnerWithSystemEnvironment(env)
				.withPropertyValues(
						"github.owner=" + VALID_OWNER,
						"github.token=" + TOKEN_SENTINEL)
				.run(context -> {
					assertNull(context.getStartupFailure(), "context should have started");
					GithubProperties properties = context.getBean(GithubProperties.class);
					assertEquals(60000, ReflectionTestUtils.getField(properties, "cacheTtlMs"));
				});
	}

	@Test
	void envVarGITHUB_CACHE_MAXIMUM_SIZEBoundViaSystemEnvironmentSource() {
		Map<String, Object> env = new HashMap<>();
		env.put("GITHUB_CACHE_MAXIMUM_SIZE", "9");
		envRunnerWithSystemEnvironment(env)
				.withPropertyValues(
						"github.owner=" + VALID_OWNER,
						"github.token=" + TOKEN_SENTINEL)
				.run(context -> {
					assertNull(context.getStartupFailure(), "context should have started");
					GithubProperties properties = context.getBean(GithubProperties.class);
					assertEquals(9, ReflectionTestUtils.getField(properties, "cacheMaximumSize"));
				});
	}

	private ApplicationContextRunner envRunnerWithSystemEnvironment(Map<String, Object> env) {
		return new ApplicationContextRunner()
				.withUserConfiguration(GithubPropertiesConfig.class)
				.withInitializer(context -> {
					ConfigDataEnvironmentPostProcessor.applyTo(context.getEnvironment());
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					context.getEnvironment().getPropertySources()
							.addLast(new SystemEnvironmentPropertySource("systemEnvironment", env));
				});
	}

	private void assertStartupFailsWith(String expectedMessage, String... properties) {
		runner.withPropertyValues(properties).run(context -> {
			Throwable failure = context.getStartupFailure();
			assertNotNull(failure, "context should have failed to start");
			Throwable rootCause = rootCauseOf(failure);
			assertEquals(IllegalStateException.class, rootCause.getClass());
			assertEquals(expectedMessage, rootCause.getMessage());
			for (String message : messagesOf(failure)) {
				assertFalse(message.contains(TOKEN_SENTINEL), "token sentinel leaked into a failure message");
			}
		});
	}

	private static Throwable rootCauseOf(Throwable failure) {
		Throwable current = failure;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private static List<String> messagesOf(Throwable failure) {
		List<String> messages = new ArrayList<>();
		Throwable current = failure;
		while (current != null) {
			if (current.getMessage() != null) {
				messages.add(current.getMessage());
			}
			current = current.getCause();
		}
		return messages;
	}
}
