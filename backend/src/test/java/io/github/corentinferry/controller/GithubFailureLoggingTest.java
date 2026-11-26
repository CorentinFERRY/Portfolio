package io.github.corentinferry.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import io.github.corentinferry.github.client.GithubApiClient;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import io.github.corentinferry.selection.RepositorySelection;
import io.github.corentinferry.selection.RepositorySelectionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GithubFailureLoggingTest {

	private static final String LEAK_MESSAGE = "LEAK_MESSAGE";
	private static final String ADVICE_LOGGER = "io.github.corentinferry.controller.GithubFailureAdvice";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RepositorySelectionRepository selectionRepository;

	@MockitoBean
	private GithubApiClient githubApiClient;

	private Logger adviceLogger;
	private ListAppender<ILoggingEvent> appender;
	private Level previousLevel;

	@BeforeEach
	void setUp() {
		selectionRepository.deleteAll();
		selectionRepository.save(new RepositorySelection(1L));

		adviceLogger = (Logger) LoggerFactory.getLogger(ADVICE_LOGGER);
		previousLevel = adviceLogger.getLevel();
		adviceLogger.setLevel(Level.WARN);
		appender = new ListAppender<>();
		appender.start();
		adviceLogger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		adviceLogger.detachAppender(appender);
		adviceLogger.setLevel(previousLevel);
		appender.stop();
	}

	@Test
	void warnLogsOnlyExceptionClassNameWithoutSentinel() throws Exception {
		when(githubApiClient.listPublicRepositories())
				.thenThrow(new GithubUnavailableException(LEAK_MESSAGE));

		mockMvc.perform(get("/api/projects"))
				.andExpect(status().isServiceUnavailable());

		List<ILoggingEvent> warnings = appender.list.stream()
				.filter(event -> event.getLevel() == Level.WARN)
				.toList();
		assertEquals(1, warnings.size());
		assertEquals("GithubUnavailableException", warnings.get(0).getFormattedMessage());
		assertNull(warnings.get(0).getThrowableProxy());
		assertFalse(containsSentinel(appender.list, LEAK_MESSAGE));
	}

	private static boolean containsSentinel(List<ILoggingEvent> events, String sentinel) {
		for (ILoggingEvent event : events) {
			String message = event.getFormattedMessage();
			if (message != null && message.contains(sentinel)) {
				return true;
			}
			Object[] arguments = event.getArgumentArray();
			if (arguments != null) {
				for (Object argument : arguments) {
					if (argument != null && argument.toString().contains(sentinel)) {
						return true;
					}
				}
			}
			if (containsInThrowable(event.getThrowableProxy(), sentinel)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsInThrowable(IThrowableProxy throwable, String sentinel) {
		if (throwable == null) {
			return false;
		}
		if (throwable.getMessage() != null && throwable.getMessage().contains(sentinel)) {
			return true;
		}
		StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
		if (frames != null) {
			for (StackTraceElementProxy frame : frames) {
				if (frame != null && frame.toString().contains(sentinel)) {
					return true;
				}
			}
		}
		for (IThrowableProxy suppressed : throwable.getSuppressed()) {
			if (containsInThrowable(suppressed, sentinel)) {
				return true;
			}
		}
		return containsInThrowable(throwable.getCause(), sentinel);
	}
}
