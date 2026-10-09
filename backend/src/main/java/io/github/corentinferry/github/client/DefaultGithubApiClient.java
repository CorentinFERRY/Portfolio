package io.github.corentinferry.github.client;

import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.exception.GithubUnexpectedResponseException;
import io.github.corentinferry.github.model.GithubRepository;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class DefaultGithubApiClient implements GithubApiClient {

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
		org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
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
		String url = base + "/users/" + properties.getOwner() + "/repos";
		URI uri = URI.create(url);
		String body = restClient.get()
				.uri(uriBuilder -> uriBuilder.scheme(uri.getScheme()).host(uri.getHost()).port(uri.getPort())
						.path(uri.getPath())
						.queryParam("type", "owner")
						.queryParam("sort", "pushed")
						.queryParam("direction", "desc")
						.queryParam("per_page", "100")
						.queryParam("page", "1")
						.build())
				.header("Authorization", "Bearer " + properties.getToken())
				.header("User-Agent", "portfolio-backend")
				.header("X-GitHub-Api-Version", GithubProperties.GITHUB_API_VERSION)
				.retrieve()
				.body(String.class);
		return parse(body);
	}

	private List<GithubRepository> parse(String body) {
		try {
			JsonNode root = objectMapper.readTree(body);
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
			throw new GithubUnexpectedResponseException("Unexpected response");
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
		String description = node.has("description") && node.get("description").isNull() ? null : (node.has("description") && node.get("description").isTextual() ? node.get("description").asString() : null);
		String htmlUrl = node.get("html_url").asString();
		String homepage = node.has("homepage") && node.get("homepage").isNull() ? null : (node.has("homepage") && node.get("homepage").isTextual() ? node.get("homepage").asString() : null);
		String language = node.has("language") && node.get("language").isNull() ? null : (node.has("language") && node.get("language").isTextual() ? node.get("language").asString() : null);
		long stargazersCount = node.get("stargazers_count").longValue();
		boolean fork = node.get("fork").asBoolean();
		boolean archived = node.get("archived").asBoolean();
		String pushedAt = node.has("pushed_at") && node.get("pushed_at").isNull() ? null : (node.has("pushed_at") && node.get("pushed_at").isTextual() ? node.get("pushed_at").asString() : null);
		return new GithubRepository(id, name, description, htmlUrl, homepage, language, topics, stargazersCount, fork, archived, pushedAt);
	}
}
