package io.github.corentinferry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

/**
 * Browser origins allowed to read API responses cross-origin.
 *
 * <p>Each origin must be exact: https, or http on a loopback host with an
 * explicit port for local development. Wildcards, paths and any other form
 * stop the application at startup, so a configuration mistake can never
 * silently widen the policy. The failure message never repeats the value.
 */
@ConfigurationProperties(prefix = "portfolio.cors")
public record CorsProperties(List<String> allowedOrigins) {

	private static final String INVALID_ORIGIN_MESSAGE = "portfolio.cors.allowed-origins contains an invalid origin";
	private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

	public CorsProperties {
		if (allowedOrigins == null) {
			allowedOrigins = List.of();
		}
		for (String origin : allowedOrigins) {
			if (!isValidOrigin(origin)) {
				throw new IllegalStateException(INVALID_ORIGIN_MESSAGE);
			}
		}
		allowedOrigins = List.copyOf(allowedOrigins);
	}

	private static boolean isValidOrigin(String origin) {
		if (origin == null || origin.isBlank()) {
			return false;
		}
		URI uri;
		try {
			uri = new URI(origin);
		} catch (URISyntaxException e) {
			return false;
		}
		// A wildcard host or port leaves no parsed host, which rejects "*" patterns too.
		if (uri.getHost() == null || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty()
				|| uri.getRawQuery() != null || uri.getRawFragment() != null) {
			return false;
		}
		if ("https".equals(uri.getScheme())) {
			return true;
		}
		return "http".equals(uri.getScheme()) && LOOPBACK_HOSTS.contains(uri.getHost()) && uri.getPort() != -1;
	}
}
