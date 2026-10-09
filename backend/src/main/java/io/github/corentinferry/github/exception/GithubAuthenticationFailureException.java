package io.github.corentinferry.github.exception;

public class GithubAuthenticationFailureException extends GithubApiException {
	public GithubAuthenticationFailureException(String message) {
		super(message);
	}
}
