package io.github.corentinferry.github.exception;

public class GithubPaginationViolationException extends GithubApiException {
	public GithubPaginationViolationException(String message) {
		super(message);
	}
}
