package io.github.corentinferry.github.config;

import io.github.corentinferry.config.GithubPropertiesConfig;
import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GithubPropertiesStartupValidationTest {

	private static final String VALID_OWNER = "valid-owner";
	private static final String TOKEN_SENTINEL = "sentinel-token-7f3c9a";
	private static final String INVALID_OWNER = "invalid_owner_4b8e2c";
	private static final String INSECURE_BASE_URL = "http://insecure.example.invalid/portfolio";
	private static final String LOCALHOST_EVIL_BASE_URL = "http://localhost.evil.example";
	private static final String LOOPBACK_IP_EVIL_BASE_URL = "http://127.0.0.1.evil.example";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(GithubPropertiesConfig.class)
			.withInitializer(context -> {
				context.getEnvironment().getPropertySources()
						.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
				context.getEnvironment().getPropertySources()
						.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
			});

	@Test
	void invalidOwnerStopsStartupWithoutEchoingPropertyValues() {
		assertStartupFailsWith("github.owner is invalid",
				"github.owner=" + INVALID_OWNER,
				"github.token=" + TOKEN_SENTINEL);
	}

	@Test
	void missingOwnerStopsStartup() {
		assertStartupFailsWith("github.owner is required",
				"github.token=" + TOKEN_SENTINEL);
	}

	@Test
	void missingTokenStopsStartup() {
		assertStartupFailsWith("github.token is required",
				"github.owner=" + VALID_OWNER);
	}

	@Test
	void blankTokenStopsStartup() {
		assertStartupFailsWith("github.token is required",
				"github.owner=" + VALID_OWNER,
				"github.token=");
	}

	@Test
	void insecureBaseUrlStopsStartup() {
		assertStartupFailsWith("github.api-base-url must use https",
				"github.owner=" + VALID_OWNER,
				"github.token=" + TOKEN_SENTINEL,
				"github.api-base-url=" + INSECURE_BASE_URL);
	}

	@Test
	void blankOwnerStopsStartup() {
		assertStartupFailsWith("github.owner is required",
				"github.owner=",
				"github.token=" + TOKEN_SENTINEL);
	}

	@Test
	void localhostEvilHostBaseUrlStopsStartup() {
		assertStartupFailsWith("github.api-base-url must use https",
				"github.owner=" + VALID_OWNER,
				"github.token=" + TOKEN_SENTINEL,
				"github.api-base-url=" + LOCALHOST_EVIL_BASE_URL);
	}

	@Test
	void loopbackIpEvilHostBaseUrlStopsStartup() {
		assertStartupFailsWith("github.api-base-url must use https",
				"github.owner=" + VALID_OWNER,
				"github.token=" + TOKEN_SENTINEL,
				"github.api-base-url=" + LOOPBACK_IP_EVIL_BASE_URL);
	}

	@Test
	void validValuesStartContextAndExposeBoundProperties() {
		runner.withPropertyValues(
						"github.owner=" + VALID_OWNER,
						"github.token=" + TOKEN_SENTINEL)
				.run(context -> {
					assertNull(context.getStartupFailure(), "context should have started");
					GithubProperties properties = context.getBean(GithubProperties.class);
					assertEquals(VALID_OWNER, properties.getOwner());
					assertEquals(TOKEN_SENTINEL, properties.getToken());
				});
	}

	@Test
	void httpLoopbackBaseUrlStartsContext() {
		runner.withPropertyValues(
						"github.owner=" + VALID_OWNER,
						"github.token=" + TOKEN_SENTINEL,
						"github.api-base-url=http://localhost:8089")
				.run(context -> {
					assertNull(context.getStartupFailure(), "context should have started");
					assertEquals("http://localhost:8089",
							context.getBean(GithubProperties.class).getApiBaseUrl());
				});
	}

	private void assertStartupFailsWith(String expectedMessage, String... properties) {
		runner.withPropertyValues(properties).run(context -> {
			Throwable failure = context.getStartupFailure();
			assertNotNull(failure, "context should have failed to start");
			Throwable rootCause = rootCauseOf(failure);
			assertEquals(IllegalStateException.class, rootCause.getClass());
			assertEquals(expectedMessage, rootCause.getMessage());
			assertNoPropertyValueLeaked(failure);
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

	private static void assertNoPropertyValueLeaked(Throwable failure) {
		for (String message : messagesOf(failure)) {
			assertFalse(message.contains(TOKEN_SENTINEL), "token sentinel leaked into a failure message");
			assertFalse(message.contains(INVALID_OWNER), "invalid owner leaked into a failure message");
			assertFalse(message.contains(INSECURE_BASE_URL), "base URL leaked into a failure message");
			assertFalse(message.contains(LOCALHOST_EVIL_BASE_URL), "base URL leaked into a failure message");
			assertFalse(message.contains(LOOPBACK_IP_EVIL_BASE_URL), "base URL leaked into a failure message");
		}
	}
}
