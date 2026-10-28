package io.github.corentinferry.github.client;

import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubAccessDeniedException;
import io.github.corentinferry.github.exception.GithubAuthenticationFailureException;
import io.github.corentinferry.github.exception.GithubOwnerNotFoundException;
import io.github.corentinferry.github.exception.GithubPaginationViolationException;
import io.github.corentinferry.github.exception.GithubRateLimitExceededException;
import io.github.corentinferry.github.exception.GithubUnexpectedResponseException;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import io.github.corentinferry.github.model.GithubRepository;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultGithubApiClient implements GithubApiClient {

	private static final int MAX_PAGES = 10;
	private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");
	private static final Logger LOG = LoggerFactory.getLogger(DefaultGithubApiClient.class);

	private final GithubProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public DefaultGithubApiClient(GithubProperties properties) {
		this(properties, createRestClient(properties), new ObjectMapper());
	}

	public DefaultGithubApiClient(GithubProperties properties, RestClient restClient, ObjectMapper objectMapper) {
		this.properties = properties;
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	private static RestClient createRestClient(GithubProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
			@Override
			protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
				super.prepareConnection(connection, httpMethod);
				connection.setInstanceFollowRedirects(false);
			}
		};
		factory.setConnectTimeout(properties.getConnectTimeout());
		factory.setReadTimeout(properties.getReadTimeout());
		return RestClient.builder().requestFactory(factory).build();
	}

	@Override
	public List<GithubRepository> listPublicRepositories() {
		String base = properties.getApiBaseUrl();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		URI baseUri = URI.create(base);
		String nextUrl = base + "/users/" + properties.getOwner() + "/repos?type=owner&sort=pushed&direction=desc&per_page=100&page=1";
		List<GithubRepository> all = new ArrayList<>();
		int pages = 0;
		while (nextUrl != null && pages < MAX_PAGES) {
			pages++;
			URI uri;
			try {
				uri = URI.create(nextUrl);
			} catch (IllegalArgumentException e) {
				throw new GithubPaginationViolationException("Pagination violation");
			}
			ensureSameOrigin(baseUri, uri);
			try {
				ResponseEntity<String> response = restClient.get()
						.uri(uri)
						.header("Authorization", "Bearer " + properties.getToken())
						.header("User-Agent", "portfolio-backend")
						.header("X-GitHub-Api-Version", GithubProperties.GITHUB_API_VERSION)
						.retrieve()
						.toEntity(String.class);
				HttpStatusCode status = response.getStatusCode();
				int code = status.value();
				if (code >= 300 && code < 400) {
					throw new GithubUnexpectedResponseException("Unexpected response");
				}
				String body = response.getBody() == null ? "[]" : response.getBody();
				all.addAll(parse(body));
				String link = response.getHeaders().getFirst("Link");
				nextUrl = extractNext(link);
			} catch (RestClientResponseException e) {
				throw mapException(e);
			} catch (ResourceAccessException e) {
				throw new GithubUnavailableException("Unavailable");
			} catch (io.github.corentinferry.github.exception.GithubApiException e) {
				throw e;
			} catch (Exception e) {
				throw mapUnexpected(e);
			}
		}
		if (nextUrl != null) {
			throw new GithubPaginationViolationException("Pagination violation");
		}
		return all;
	}

	private RuntimeException mapException(RestClientResponseException e) {
		HttpStatusCode status = e.getStatusCode();
		int code = status.value();
		if (code == 401) {
			return new GithubAuthenticationFailureException("Authentication failure");
		}
		if (code == 403) {
			String remaining = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("x-ratelimit-remaining") : null;
			String retryAfter = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
			String reset = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("x-ratelimit-reset") : null;
			if ("0".equals(remaining) || retryAfter != null) {
				Long resetVal = null;
				try {
					if (reset != null) resetVal = Long.parseLong(reset);
				} catch (Exception ex) {
					resetVal = null;
				}
				return new GithubRateLimitExceededException("Rate limit exceeded", resetVal);
			}
			return new GithubAccessDeniedException("Access denied");
		}
		if (code == 404) {
			return new GithubOwnerNotFoundException("Owner not found");
		}
		if (code == 429) {
			String reset = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("x-ratelimit-reset") : null;
			Long resetVal = null;
			try {
				if (reset != null) resetVal = Long.parseLong(reset);
			} catch (Exception ex) {
				resetVal = null;
			}
			return new GithubRateLimitExceededException("Rate limit exceeded", resetVal);
		}
		if (code >= 500) {
			return new GithubUnavailableException("Unavailable");
		}
		if (code >= 300 && code < 400) {
			return new GithubUnexpectedResponseException("Unexpected response");
		}
		return new GithubUnexpectedResponseException("Unexpected response");
	}

	private RuntimeException mapUnexpected(Exception e) {
		LOG.warn("Unexpected error while calling GitHub API: {}", e.getClass().getName());
		return new GithubUnexpectedResponseException("Unexpected response");
	}


	private void ensureSameOrigin(URI base, URI candidate) {
		if (candidate.getUserInfo() != null) {
			throw new GithubPaginationViolationException("Pagination violation");
		}
		if (candidate.getScheme() == null || base.getScheme() == null || !candidate.getScheme().equals(base.getScheme())) {
			throw new GithubPaginationViolationException("Pagination violation");
		}
		String ch = candidate.getHost();
		String bh = base.getHost();
		if (ch == null || bh == null || !ch.equals(bh)) {
			throw new GithubPaginationViolationException("Pagination violation");
		}
		int cp = candidate.getPort();
		int bp = base.getPort();
		int cpn = (cp == -1) ? getDefaultPort(candidate.getScheme()) : cp;
		int bpn = (bp == -1) ? getDefaultPort(base.getScheme()) : bp;
		if (cpn != bpn) {
			throw new GithubPaginationViolationException("Pagination violation");
		}
	}

	private int getDefaultPort(String scheme) {
		if ("https".equals(scheme)) {
			return 443;
		}
		if ("http".equals(scheme)) {
			return 80;
		}
		return -1;
	}

	private String extractNext(String link) {
		if (link == null || link.isBlank()) {
			return null;
		}
		Matcher m = LINK_PATTERN.matcher(link);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	private List<GithubRepository> parse(String body) {
		try {
			JsonNode root;
			try {
				root = objectMapper.readTree(body);
			} catch (tools.jackson.core.JacksonException e) {
				throw new GithubUnexpectedResponseException("Unexpected response");
			}
			if (!root.isArray()) {
				throw new GithubUnexpectedResponseException("Unexpected response");
			}
			List<GithubRepository> result = new ArrayList<>();
			for (JsonNode node : root) {
				if (!node.has("private") || !node.get("private").isBoolean()) {
					throw new GithubUnexpectedResponseException("Unexpected response");
				}
				if (node.get("private").asBoolean()) {
					continue;
				}
				result.add(map(node));
			}
			return result;
		} catch (Exception e) {
			if (e instanceof io.github.corentinferry.github.exception.GithubApiException) {
				throw (io.github.corentinferry.github.exception.GithubApiException) e;
			}
			throw mapUnexpected(e);
		}
	}

	private GithubRepository map(JsonNode node) {
		if (!node.has("id") || !node.get("id").isNumber()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (!node.has("name") || !node.get("name").isTextual()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (!node.has("html_url") || !node.get("html_url").isTextual()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (!node.has("stargazers_count") || !node.get("stargazers_count").isNumber()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (!node.has("fork") || !node.get("fork").isBoolean()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (!node.has("archived") || !node.get("archived").isBoolean()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (node.has("description") && !node.get("description").isTextual() && !node.get("description").isNull()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (node.has("homepage") && !node.get("homepage").isTextual() && !node.get("homepage").isNull()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (node.has("language") && !node.get("language").isTextual() && !node.get("language").isNull()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		if (node.has("pushed_at") && !node.get("pushed_at").isTextual() && !node.get("pushed_at").isNull()) {
			throw new GithubUnexpectedResponseException("Unexpected response");
		}
		List<String> topics = List.of();
		if (node.has("topics")) {
			JsonNode tnode = node.get("topics");
			if (tnode.isArray()) {
				List<String> t = new ArrayList<>();
				for (JsonNode n : tnode) {
					if (!n.isTextual()) {
						throw new GithubUnexpectedResponseException("Unexpected response");
					}
					t.add(n.asString());
				}
				topics = t;
			} else if (tnode.isNull()) {
				topics = List.of();
			} else {
				throw new GithubUnexpectedResponseException("Unexpected response");
			}
		}
		long id = node.get("id").longValue();
		String name = node.get("name").asString();
		String description = (node.has("description") && node.get("description").isNull()) ? null : (node.has("description") && node.get("description").isTextual() ? node.get("description").asString() : null);
		String htmlUrl = node.get("html_url").asString();
		String homepage = (node.has("homepage") && node.get("homepage").isNull()) ? null : (node.has("homepage") && node.get("homepage").isTextual() ? node.get("homepage").asString() : null);
		String language = (node.has("language") && node.get("language").isNull()) ? null : (node.has("language") && node.get("language").isTextual() ? node.get("language").asString() : null);
		long stargazersCount = node.get("stargazers_count").longValue();
		boolean fork = node.get("fork").asBoolean();
		boolean archived = node.get("archived").asBoolean();
		String pushedAt = (node.has("pushed_at") && node.get("pushed_at").isNull()) ? null : (node.has("pushed_at") && node.get("pushed_at").isTextual() ? node.get("pushed_at").asString() : null);
		return new GithubRepository(id, name, description, htmlUrl, homepage, language, topics, stargazersCount, fork, archived, pushedAt);
	}
}
