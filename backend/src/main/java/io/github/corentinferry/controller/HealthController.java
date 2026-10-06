package io.github.corentinferry.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness endpoint of the portfolio API.
 */
@RestController
public class HealthController {

	/** Response body of GET /api/health. */
	public record HealthResponse(String status) {
	}

	@GetMapping("/api/health")
	public HealthResponse health() {
		return new HealthResponse("UP");
	}

}
