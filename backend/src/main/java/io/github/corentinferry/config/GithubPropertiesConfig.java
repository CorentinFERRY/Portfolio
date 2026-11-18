package io.github.corentinferry.config;

import io.github.corentinferry.github.GithubProperties;
import io.github.corentinferry.github.client.DefaultGithubApiClient;
import io.github.corentinferry.github.client.GithubApiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GithubProperties.class)
public class GithubPropertiesConfig {

	@Bean
	public GithubApiClient githubApiClient(GithubProperties properties) {
		return new DefaultGithubApiClient(properties);
	}
}
