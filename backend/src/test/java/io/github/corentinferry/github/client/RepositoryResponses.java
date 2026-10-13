package io.github.corentinferry.github.client;

final class RepositoryResponses {

	private RepositoryResponses() {
	}

	static String repositoryJson(long id, String name, boolean privateRepository) {
		return "{\"id\":" + id
				+ ",\"name\":\"" + name + "\""
				+ ",\"description\":\"desc\""
				+ ",\"html_url\":\"https://example.com/fictional-owner/" + name + "\""
				+ ",\"homepage\":\"https://example.com\""
				+ ",\"language\":\"Java\""
				+ ",\"topics\":[\"demo\"]"
				+ ",\"stargazers_count\":5"
				+ ",\"fork\":false"
				+ ",\"archived\":false"
				+ ",\"pushed_at\":\"2026-10-09T00:00:00Z\""
				+ ",\"private\":" + privateRepository
				+ ",\"unknown_field\":\"ignored\"}";
	}

	static String repositoryPage(long id, String name, boolean privateRepository) {
		return "[" + repositoryJson(id, name, privateRepository) + "]";
	}

	static String singlePublicRepositoryPage() {
		return repositoryPage(1, "r1", false);
	}
}
