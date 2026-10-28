package io.github.corentinferry.github.exception;

public abstract class GithubApiException extends RuntimeException {
	protected GithubApiException(String message) {
		super(message);
	}
}
