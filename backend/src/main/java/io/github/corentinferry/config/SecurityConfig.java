package io.github.corentinferry.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Denies every request by default so new routes stay private until they are
 * explicitly permitted here.
 */
@Configuration(proxyBeanMethods = false)
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
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
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

}
