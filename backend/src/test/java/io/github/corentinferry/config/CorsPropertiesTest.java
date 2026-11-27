package io.github.corentinferry.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that the CORS allow list only accepts exact https origins, or http
 * on a loopback host with an explicit port, and that a bad value stops the
 * application at startup instead of silently widening the policy.
 */
class CorsPropertiesTest {

	private static final String INVALID_ORIGIN_MESSAGE = "portfolio.cors.allowed-origins contains an invalid origin";
	private static final String ENV_VARIABLE = "PORTFOLIO_CORS_ALLOWED_ORIGINS";

	@ParameterizedTest
	@ValueSource(strings = {
			"https://portfolio.example",
			"https://portfolio.example:8443",
			"http://localhost:5173",
			"http://127.0.0.1:4173"})
	void acceptsHttpsOriginsAndLoopbackHttpOriginsWithPort(String origin) {
		List<String> origins = List.of("https://first.example", origin);

		CorsProperties properties = new CorsProperties(origins);

		assertEquals(origins, properties.allowedOrigins());
		assertThrows(UnsupportedOperationException.class,
				() -> properties.allowedOrigins().add("https://added.example"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"*",
			"https://*.portfolio.example",
			"https://portfolio.example:*",
			"http://portfolio.example",
			"http://localhost",
			"http://127.0.0.1",
			"http://localhost.evil.example:5173",
			"http://127.0.0.1.evil.example:5173",
			"http://[::1]:5173",
			"https://portfolio.example/",
			"https://portfolio.example/path",
			"https://user@portfolio.example",
			"https://portfolio.example?q=1",
			"https://portfolio.example#fragment",
			"ftp://portfolio.example",
			"[not-a-uri",
			"",
			" "})
	void rejectsOriginsOutsideThePolicyWithoutEchoingThem(String origin) {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> new CorsProperties(List.of("https://first.example", origin)));

		assertEquals(INVALID_ORIGIN_MESSAGE, failure.getMessage());
		if (!origin.isBlank()) {
			assertFalse(failure.getMessage().contains(origin), "origin leaked into the failure message");
		}
	}

	@Test
	void missingListBecomesEmpty() {
		assertEquals(List.of(), new CorsProperties(null).allowedOrigins());
	}

	@Test
	void commaSeparatedEnvironmentVariableBindsTrimmedOrigins() {
		runnerWithEnvironment(Map.of(ENV_VARIABLE, "https://portfolio.example, http://localhost:5173"))
				.run(context -> {
					assertNull(context.getStartupFailure(), "context should have started");
					assertEquals(List.of("https://portfolio.example", "http://localhost:5173"),
							context.getBean(CorsProperties.class).allowedOrigins());
				});
	}

	@Test
	void invalidOriginInEnvironmentVariableStopsStartupWithoutEchoingIt() {
		String invalid = "https://portfolio.example/path";
		runnerWithEnvironment(Map.of(ENV_VARIABLE, "http://localhost:5173," + invalid))
				.run(context -> {
					Throwable failure = context.getStartupFailure();
					assertNotNull(failure, "context should have failed to start");
					Throwable rootCause = rootCauseOf(failure);
					assertEquals(IllegalStateException.class, rootCause.getClass());
					assertEquals(INVALID_ORIGIN_MESSAGE, rootCause.getMessage());
					for (String message : messagesOf(failure)) {
						assertFalse(message.contains(invalid), "origin leaked into a failure message");
					}
				});
	}

	@Test
	void applicationYmlDefaultsToAnEmptyListWithoutTheVariable() {
		runnerWithEnvironment(Map.of())
				.run(context -> {
					assertNull(context.getStartupFailure(), "context should have started");
					assertEquals(List.of(), context.getBean(CorsProperties.class).allowedOrigins());
				});
	}

	/**
	 * Loads application.yml and replaces the real environment with the given
	 * variables, so an exported variable in the shell cannot change a result.
	 */
	private static ApplicationContextRunner runnerWithEnvironment(Map<String, Object> variables) {
		return new ApplicationContextRunner()
				.withUserConfiguration(CorsPropertiesConfiguration.class)
				.withInitializer(context -> {
					ConfigDataEnvironmentPostProcessor.applyTo(context.getEnvironment());
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					context.getEnvironment().getPropertySources()
							.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
					context.getEnvironment().getPropertySources()
							.addLast(new SystemEnvironmentPropertySource(
									StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
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

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(CorsProperties.class)
	static class CorsPropertiesConfiguration {
	}
}
