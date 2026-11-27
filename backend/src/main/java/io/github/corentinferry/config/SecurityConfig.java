package io.github.corentinferry.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Denies every request by default so new routes stay private until they are
 * explicitly permitted here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

	/**
	 * The API only returns JSON, so a browser rendering any response must load
	 * nothing and must not frame it. This is a constant, not a property: a
	 * security policy is not environment configuration, and an environment
	 * variable must not be able to weaken it.
	 */
	private static final String API_CONTENT_SECURITY_POLICY =
			"default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CorsProperties corsProperties) throws Exception {
		return http
				// The source is passed directly, not published as a bean: Spring
				// Security would otherwise also apply a bean found by name or type,
				// and removing this line would not remove CORS.
				.cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties.allowedOrigins())))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
						.frameOptions(frame -> frame.deny())
						.contentTypeOptions(Customizer.withDefaults()))
				.authorizeHttpRequests(authorize -> authorize
						// The container forwards a failed request to /error; denying that
						// forward would turn every 500 into an empty 403. A direct request
						// to /error is a normal dispatch and stays denied.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/projects").permitAll()
						.anyRequest().denyAll())
				.build();
	}

	private static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of(HttpMethod.GET.name()));
		// Explicit decision while the API has no cookies: the authentication
		// cookie (slice 9) must revisit this on purpose, never by default.
		configuration.setAllowCredentials(false);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		// Registered even when the list is empty: an empty configuration rejects
		// every cross-origin request with an explicit 403, whereas a missing one
		// would let the request through without CORS headers.
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

}
