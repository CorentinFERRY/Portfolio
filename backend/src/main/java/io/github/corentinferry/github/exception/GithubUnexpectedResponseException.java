package io.github.corentinferry.github.exception;

public class GithubUnexpectedResponseException extends GithubApiException {
	public GithubUnexpectedResponseException(String message) {
		super(message);
	}
}
