package io.github.corentinferry.github.exception;

public class GithubRateLimitExceededException extends GithubApiException {
	private final Long resetEpochSeconds;

	public GithubRateLimitExceededException(String message, Long resetEpochSeconds) {
		super(message);
		this.resetEpochSeconds = resetEpochSeconds;
	}

	public Long getResetEpochSeconds() {
		return resetEpochSeconds;
	}
}
