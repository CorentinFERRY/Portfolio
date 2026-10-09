package io.github.corentinferry.config;

import io.github.corentinferry.github.GithubProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GithubProperties.class)
public class GithubPropertiesConfig {
}
