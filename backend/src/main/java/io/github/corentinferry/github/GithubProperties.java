package io.github.corentinferry.github;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "github")
public class GithubProperties {

	public static final String GITHUB_API_VERSION = "2022-11-28";
	private static final int DEFAULT_CACHE_TTL_MS = 300_000;
	private static final int DEFAULT_CACHE_MAXIMUM_SIZE = 100;

	private final String token;
	private final String owner;
	private final String apiBaseUrl;
	private final int connectTimeout;
	private final int readTimeout;
	private final int cacheTtlMs;
	private final int cacheMaximumSize;

	public GithubProperties(String token, String owner, String apiBaseUrl, Integer connectTimeout, Integer readTimeout) {
		this(token, owner, apiBaseUrl, connectTimeout, readTimeout,
				DEFAULT_CACHE_TTL_MS, DEFAULT_CACHE_MAXIMUM_SIZE);
	}

	@ConstructorBinding
	public GithubProperties(String token, String owner, String apiBaseUrl, Integer connectTimeout, Integer readTimeout,
			Integer cacheTtlMs, Integer cacheMaximumSize) {
		this.token = token;
		this.owner = owner;
		this.apiBaseUrl = apiBaseUrl != null && !apiBaseUrl.isBlank() ? apiBaseUrl : "https://api.github.com";
		this.connectTimeout = connectTimeout != null ? connectTimeout : 3000;
		this.readTimeout = readTimeout != null ? readTimeout : 10000;
		this.cacheTtlMs = cacheTtlMs != null ? cacheTtlMs : DEFAULT_CACHE_TTL_MS;
		this.cacheMaximumSize = cacheMaximumSize != null ? cacheMaximumSize : DEFAULT_CACHE_MAXIMUM_SIZE;
		validate();
	}

	private void validate() {
		if (owner == null || owner.isBlank()) {
			throw new IllegalStateException("github.owner is required");
		}
		if (!isValidOwner(owner)) {
			throw new IllegalStateException("github.owner is invalid");
		}
		if (token == null || token.isBlank()) {
			throw new IllegalStateException("github.token is required");
		}
		if (cacheTtlMs <= 0) {
			throw new IllegalStateException("github.cache-ttl-ms must be positive");
		}
		if (cacheMaximumSize <= 0) {
			throw new IllegalStateException("github.cache-maximum-size must be positive");
		}
		validateBaseUrl();
	}

	private boolean isValidOwner(String o) {
		if (o.length() < 1 || o.length() > 39) {
			return false;
		}
		if (o.startsWith("-") || o.endsWith("-")) {
			return false;
		}
		if (o.contains("--")) {
			return false;
		}
		for (int i = 0; i < o.length(); i++) {
			char c = o.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == '-')) {
				return false;
			}
		}
		return true;
	}

	private void validateBaseUrl() {
		if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
			throw new IllegalStateException("github.api-base-url is required");
		}
		java.net.URI uri;
		try {
			uri = java.net.URI.create(apiBaseUrl);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("github.api-base-url is invalid");
		}
		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (scheme == null || !(scheme.equals("https") || isLoopbackHost(host))) {
			throw new IllegalStateException("github.api-base-url must use https");
		}
	}

	private boolean isLoopbackHost(String host) {
		if (host == null) {
			return false;
		}
		return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
	}

	public String getToken() {
		return token;
	}

	public String getOwner() {
		return owner;
	}

	public String getApiBaseUrl() {
		return apiBaseUrl;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public int getCacheTtlMs() {
		return cacheTtlMs;
	}

	public int getCacheMaximumSize() {
		return cacheMaximumSize;
	}

	@Override
	public String toString() {
		return "GithubProperties{" +
				"token='[REDACTED]'" +
				", owner='" + owner + '\'' +
				", apiBaseUrl='" + apiBaseUrl + '\'' +
				", connectTimeout=" + connectTimeout +
				", readTimeout=" + readTimeout +
				", cacheTtlMs=" + cacheTtlMs +
				", cacheMaximumSize=" + cacheMaximumSize +
				'}';
	}
}
