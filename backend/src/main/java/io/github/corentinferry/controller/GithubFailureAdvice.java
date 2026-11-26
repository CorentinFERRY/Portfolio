package io.github.corentinferry.controller;

import io.github.corentinferry.github.exception.GithubApiException;
import io.github.corentinferry.github.exception.GithubRateLimitExceededException;
import io.github.corentinferry.github.exception.GithubUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GithubFailureAdvice {

	private static final Logger LOGGER = LoggerFactory.getLogger(GithubFailureAdvice.class);
	private static final String GITHUB_UNAVAILABLE = "github_unavailable";
	private static final String GITHUB_ERROR = "github_error";

	@ExceptionHandler({GithubUnavailableException.class, GithubRateLimitExceededException.class})
	ResponseEntity<ErrorResponse> handleUnavailable(GithubApiException exception) {
		logFailure(exception);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse(GITHUB_UNAVAILABLE));
	}

	@ExceptionHandler(GithubApiException.class)
	ResponseEntity<ErrorResponse> handleOtherGithubFailure(GithubApiException exception) {
		logFailure(exception);
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse(GITHUB_ERROR));
	}

	private static void logFailure(GithubApiException exception) {
		LOGGER.warn(exception.getClass().getSimpleName());
	}

	record ErrorResponse(String error) {
	}
}
