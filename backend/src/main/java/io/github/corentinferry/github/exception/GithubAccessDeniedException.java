package io.github.corentinferry.github.exception;

public class GithubAccessDeniedException extends GithubApiException {
	public GithubAccessDeniedException(String message) {
		super(message);
	}
}
