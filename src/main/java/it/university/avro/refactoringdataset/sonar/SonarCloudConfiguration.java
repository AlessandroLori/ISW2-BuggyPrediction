package it.university.avro.refactoringdataset.sonar;

public record SonarCloudConfiguration(
        String baseUrl,
        String projectKey,
        String branch,
        String token,
        boolean failOnUnavailable
) {
    public SonarCloudConfiguration {
        baseUrl = normalizeBaseUrl(baseUrl);
        projectKey = projectKey == null ? "" : projectKey.trim();
        branch = branch == null ? "" : branch.trim();
        token = token == null ? "" : token.trim();
    }

    public boolean isEnabled() {
        return !baseUrl.isBlank() && !projectKey.isBlank();
    }

    private static String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            return "";
        }

        String normalized = rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
