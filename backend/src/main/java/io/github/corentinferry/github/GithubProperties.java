package io.github.corentinferry.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public class GithubProperties {

	public static final String GITHUB_API_VERSION = "2022-11-28";

	private String token;
	private String owner;
	private String apiBaseUrl = "https://api.github.com";
	private int connectTimeout = 3000;
	private int readTimeout = 10000;

	public GithubProperties() {
	}

	public GithubProperties(String token, String owner, String apiBaseUrl, Integer connectTimeout, Integer readTimeout) {
		this.token = token;
		this.owner = owner;
		if (apiBaseUrl != null && !apiBaseUrl.isBlank()) {
			this.apiBaseUrl = apiBaseUrl;
		}
		if (connectTimeout != null) {
			this.connectTimeout = connectTimeout;
		}
		if (readTimeout != null) {
			this.readTimeout = readTimeout;
		}
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
		try {
			java.net.URI uri = java.net.URI.create(apiBaseUrl);
			String scheme = uri.getScheme();
			String host = uri.getHost();
			if (scheme == null || !(scheme.equals("https") || isLoopbackHost(host))) {
				throw new IllegalStateException("github.api-base-url must use https");
			}
		} catch (Exception e) {
			throw new IllegalStateException("github.api-base-url is invalid");
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

	public void setToken(String token) {
		this.token = token;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getApiBaseUrl() {
		return apiBaseUrl;
	}

	public void setApiBaseUrl(String apiBaseUrl) {
		this.apiBaseUrl = apiBaseUrl;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	@Override
	public String toString() {
		return "GithubProperties{" +
				"token='[REDACTED]'" +
				", owner='" + owner + '\'' +
				", apiBaseUrl='" + apiBaseUrl + '\'' +
				", connectTimeout=" + connectTimeout +
				", readTimeout=" + readTimeout +
				'}';
	}
}
