package io.github.corentinferry.github.config;

import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubPropertiesTest {

	@Test
	void constructorValidatesOwnerNotBlank() {
		assertThrows(IllegalStateException.class, () -> new GithubProperties("token", "", null, null, null));
	}

	@Test
	void constructorValidatesOwnerFormat() {
		assertThrows(IllegalStateException.class, () -> new GithubProperties("token", "-bad", null, null, null));
		assertThrows(IllegalStateException.class, () -> new GithubProperties("token", "bad-", null, null, null));
		assertThrows(IllegalStateException.class, () -> new GithubProperties("token", "bad--name", null, null, null));
		assertDoesNotThrow(() -> new GithubProperties("token", "good-name", null, null, null));
	}

	@Test
	void constructorValidatesTokenNotBlank() {
		assertThrows(IllegalStateException.class, () -> new GithubProperties("", "owner", null, null, null));
	}

	@Test
	void toStringRedactsToken() {
		GithubProperties p = new GithubProperties("secret-token", "owner", "https://api.github.com", 3000, 5000);
		String s = p.toString();
		assertTrue(s.contains("[REDACTED]"));
		assertTrue(!s.contains("secret-token"));
	}
}
