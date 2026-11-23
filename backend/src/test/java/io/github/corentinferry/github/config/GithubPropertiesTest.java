package io.github.corentinferry.github.config;

import io.github.corentinferry.github.GithubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void constructorRejectsNonPositiveCacheTtl() {
		IllegalStateException zero = assertThrows(IllegalStateException.class, () -> withCache(0, 100));
		assertEquals("github.cache-ttl-ms must be positive", zero.getMessage());
		IllegalStateException negative = assertThrows(IllegalStateException.class, () -> withCache(-1, 100));
		assertEquals("github.cache-ttl-ms must be positive", negative.getMessage());
	}

	@Test
	void constructorRejectsNonPositiveCacheMaximumSize() {
		IllegalStateException zero = assertThrows(IllegalStateException.class, () -> withCache(300000, 0));
		assertEquals("github.cache-maximum-size must be positive", zero.getMessage());
		IllegalStateException negative = assertThrows(IllegalStateException.class, () -> withCache(300000, -1));
		assertEquals("github.cache-maximum-size must be positive", negative.getMessage());
	}

	@Test
	void defaultsCacheConfiguration() {
		GithubProperties p = withCache(null, null);
		assertEquals(300000, ReflectionTestUtils.getField(p, "cacheTtlMs"));
		assertEquals(100, ReflectionTestUtils.getField(p, "cacheMaximumSize"));
	}

	private static GithubProperties withCache(Integer ttlMs, Integer maximumSize) {
		try {
			return (GithubProperties) GithubProperties.class
					.getConstructor(String.class, String.class, String.class,
							Integer.class, Integer.class, Integer.class, Integer.class)
					.newInstance("test-token", "owner", null, null, null, ttlMs, maximumSize);
		} catch (NoSuchMethodException e) {
			throw new AssertionError("Seven-argument GithubProperties constructor is missing", e);
		} catch (java.lang.reflect.InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new AssertionError(cause);
		} catch (java.lang.ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
