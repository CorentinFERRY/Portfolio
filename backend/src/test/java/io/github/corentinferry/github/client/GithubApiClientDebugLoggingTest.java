package io.github.corentinferry.github.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubApiClientDebugLoggingTest {

	private static final String TOKEN = "debug-leak-sentinel-4b1";
	private static final String OWNER = "fictional-owner";
	private static final String SPRING_MARKER = "spring-debug-marker";
	private static final String CLIENT_MARKER = "client-debug-marker";

	private WireMockServer server;
	private GithubProperties props;
	private Logger springLogger;
	private Logger clientLogger;
	private ListAppender<ILoggingEvent> springAppender;
	private ListAppender<ILoggingEvent> clientAppender;
	private Level previousSpringLevel;
	private Level previousClientLevel;

	@BeforeEach
	void setUp() {
		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();
		props = new GithubProperties(TOKEN, OWNER, "http://localhost:" + server.port(), 3000, 5000);

		springLogger = (Logger) LoggerFactory.getLogger("org.springframework");
		clientLogger = (Logger) LoggerFactory.getLogger(DefaultGithubApiClient.class);
		previousSpringLevel = springLogger.getLevel();
		previousClientLevel = clientLogger.getLevel();
		springLogger.setLevel(Level.DEBUG);
		clientLogger.setLevel(Level.DEBUG);

		springAppender = new ListAppender<>();
		springAppender.start();
		springLogger.addAppender(springAppender);
		clientAppender = new ListAppender<>();
		clientAppender.start();
		clientLogger.addAppender(clientAppender);
	}

	@AfterEach
	void tearDown() {
		if (clientLogger != null) {
			clientLogger.setLevel(previousClientLevel);
		}
		if (springLogger != null) {
			springLogger.setLevel(previousSpringLevel);
		}
		if (clientLogger != null && clientAppender != null) {
			clientLogger.detachAppender(clientAppender);
		}
		if (springLogger != null && springAppender != null) {
			springLogger.detachAppender(springAppender);
		}
		if (clientAppender != null) {
			clientAppender.stop();
		}
		if (springAppender != null) {
			springAppender.stop();
		}
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void debugCaptureCoversSpringAndClientLoggers() {
		springLogger.debug("{}", SPRING_MARKER);
		clientLogger.debug("{}", CLIENT_MARKER);

		assertTrue(hasEvent(springAppender.list, SPRING_MARKER), "Spring logger DEBUG capture is not working");
		assertTrue(hasEvent(clientAppender.list, CLIENT_MARKER), "client logger DEBUG capture is not working");
	}

	@Test
	void debugLogsNeverContainTheToken() {
		server.stubFor(get(urlPathEqualTo("/users/" + OWNER + "/repos"))
				.willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")));

		new DefaultGithubApiClient(props).listPublicRepositories();

		server.verify(getRequestedFor(urlPathEqualTo("/users/" + OWNER + "/repos"))
				.withHeader("Authorization", equalTo("Bearer " + TOKEN)));

		long clientDebugCount = clientAppender.list.stream()
				.filter(event -> event.getLevel() == Level.DEBUG)
				.filter(event -> !containsValue(event, CLIENT_MARKER))
				.count();
		assertTrue(clientDebugCount > 0, "expected at least one DEBUG event from the client logger");

		assertNoToken(springAppender.list, "Spring");
		assertNoToken(clientAppender.list, "client");
	}

	private static void assertNoToken(java.util.List<ILoggingEvent> events, String loggerName) {
		for (ILoggingEvent event : events) {
			assertFalse(containsValue(event, TOKEN),
					"token leaked into " + loggerName + " log events at level " + event.getLevel());
		}
	}

	private static boolean hasEvent(java.util.List<ILoggingEvent> events, String value) {
		for (ILoggingEvent event : events) {
			if (containsValue(event, value)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsValue(ILoggingEvent event, String value) {
		String message = event.getFormattedMessage();
		if (message != null && message.contains(value)) {
			return true;
		}
		Object[] arguments = event.getArgumentArray();
		if (arguments != null) {
			for (Object argument : arguments) {
				if (argument != null && argument.toString().contains(value)) {
					return true;
				}
			}
		}
		return event.getThrowableProxy() != null && containsInThrowable(event.getThrowableProxy(), value);
	}

	private static boolean containsInThrowable(IThrowableProxy throwable, String value) {
		if (throwable.getMessage() != null && throwable.getMessage().contains(value)) {
			return true;
		}
		StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
		if (frames != null) {
			for (StackTraceElementProxy frame : frames) {
				if (frame != null && frame.toString().contains(value)) {
					return true;
				}
			}
		}
		IThrowableProxy[] suppressed = throwable.getSuppressed();
		if (suppressed != null) {
			for (IThrowableProxy item : suppressed) {
				if (item != null && containsInThrowable(item, value)) {
					return true;
				}
			}
		}
		IThrowableProxy cause = throwable.getCause();
		return cause != null && containsInThrowable(cause, value);
	}
}
