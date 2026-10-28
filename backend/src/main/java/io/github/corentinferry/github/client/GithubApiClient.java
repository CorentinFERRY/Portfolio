package io.github.corentinferry.github.client;

import io.github.corentinferry.github.model.GithubRepository;

import java.util.List;

public interface GithubApiClient {
	List<GithubRepository> listPublicRepositories();
}
