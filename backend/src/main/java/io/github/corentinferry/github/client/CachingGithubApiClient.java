package io.github.corentinferry.github.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.model.GithubRepository;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class CachingGithubApiClient implements GithubApiClient {

	private final GithubApiClient delegate;
	private final GithubProperties properties;
	private final Cache<String, List<GithubRepository>> cache;

	public CachingGithubApiClient(GithubApiClient delegate, GithubProperties properties) {
		this(delegate, properties, Ticker.systemTicker(), ForkJoinPool.commonPool());
	}

	public CachingGithubApiClient(GithubApiClient delegate, GithubProperties properties,
			Ticker ticker, Executor executor) {
		this.delegate = delegate;
		this.properties = properties;
		this.cache = Caffeine.newBuilder()
				.maximumSize(properties.getCacheMaximumSize())
				.expireAfterWrite(Duration.ofMillis(properties.getCacheTtlMs()))
				.ticker(ticker)
				.executor(executor)
				.build();
	}

	@Override
	public List<GithubRepository> listPublicRepositories() {
		return cache.get(properties.getOwner(), key -> List.copyOf(delegate.listPublicRepositories()));
	}
}
