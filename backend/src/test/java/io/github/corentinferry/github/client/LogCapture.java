package io.github.corentinferry.github.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LogCapture {
	public static ListAppender<ILoggingEvent> start(Class<?> clazz) {
		Logger logger = (Logger) LoggerFactory.getLogger(clazz);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return appender;
	}

	public static void stop(Class<?> clazz, ListAppender<ILoggingEvent> appender) {
		Logger logger = (Logger) LoggerFactory.getLogger(clazz);
		logger.detachAppender(appender);
	}

	public static boolean containsToken(List<ILoggingEvent> events, String token) {
		if (token == null) return false;
		for (ILoggingEvent e : events) {
			String msg = e.getFormattedMessage();
			if (msg != null && msg.contains(token)) {
				return true;
			}
			Object[] args = e.getArgumentArray();
			if (args != null) {
				for (Object a : args) {
					if (a != null && a.toString().contains(token)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
