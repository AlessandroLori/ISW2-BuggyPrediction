package it.university.avro.releasesnapshot.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class GitHubApiClient {

    private static final String API_BASE = "https://api.github.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_SLEEP_MILLIS = 1500L;

    private final String owner;
    private final String repository;
    private final String token;
    private final String apiVersion;
    private final HttpClient httpClient;

    public GitHubApiClient(
            final String owner,
            final String repository,
            final String token,
            final String apiVersion
    ) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.token = token == null ? "" : token.trim();
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion must not be null");
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Optional<JsonNode> getOptionalJson(final String apiPath) {
        try {
            return Optional.of(getJson(apiPath));
        } catch (GitHubNotFoundException exception) {
            return Optional.empty();
        }
    }

    public JsonNode getJson(final String apiPath) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                final HttpRequest request = baseRequest(apiPath).GET().build();
                final HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                final int statusCode = response.statusCode();
                final String body = response.body();

                if (statusCode == 404) {
                    throw new GitHubNotFoundException("GitHub resource not found: " + apiPath);
                }

                if (statusCode == 403 && isRateLimitExceeded(body)) {
                    throw new GitHubRateLimitException(
                            buildRateLimitMessage()
                    );
                }

                if (isRetryableStatus(statusCode)) {
                    lastFailure = new IllegalStateException(
                            "GitHub API temporary failure. Status=" + statusCode
                                    + " path=" + apiPath
                                    + " attempt=" + attempt
                    );
                    sleepBeforeRetry(attempt);
                    continue;
                }

                if (statusCode < 200 || statusCode >= 300) {
                    throw new IllegalStateException(
                            "GitHub API call failed. Status=" + statusCode
                                    + " path=" + apiPath
                                    + " body=" + body
                    );
                }

                return OBJECT_MAPPER.readTree(body);
            } catch (GitHubNotFoundException | GitHubRateLimitException exception) {
                throw exception;
            } catch (IOException exception) {
                lastFailure = new IllegalStateException(
                        "Unable to parse GitHub JSON response for " + apiPath,
                        exception
                );
                sleepBeforeRetry(attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "GitHub request interrupted for " + apiPath,
                        exception
                );
            }
        }

        throw new IllegalStateException(
                "GitHub API call failed after retries for " + apiPath,
                lastFailure
        );
    }

    public Optional<String> getOptionalCommitShaForRef(final String ref) {
        final String encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);

        try {
            final JsonNode commitResponse = getJson(
                    "/repos/" + owner + "/" + repository + "/commits/" + encodedRef
            );
            final String sha = commitResponse.path("sha").asText();

            if (sha == null || sha.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(sha.trim());
        } catch (GitHubNotFoundException exception) {
            return Optional.empty();
        }
    }

    public Path downloadZipball(final String ref) {
        final String encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);
        final String apiPath = "/repos/" + owner + "/" + repository + "/zipball/" + encodedRef;

        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                final Path tempFile = Files.createTempFile("github-archive-", ".zip");
                final HttpRequest request = baseRequest(apiPath).GET().build();

                final HttpResponse<Path> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofFile(tempFile)
                );

                final int statusCode = response.statusCode();

                if (statusCode == 403) {
                    Files.deleteIfExists(tempFile);
                    throw new GitHubRateLimitException(buildRateLimitMessage());
                }

                if (isRetryableStatus(statusCode)) {
                    lastFailure = new IllegalStateException(
                            "GitHub archive temporary failure. Status=" + statusCode
                                    + " path=" + apiPath
                                    + " attempt=" + attempt
                    );
                    Files.deleteIfExists(tempFile);
                    sleepBeforeRetry(attempt);
                    continue;
                }

                if (statusCode < 200 || statusCode >= 300) {
                    Files.deleteIfExists(tempFile);
                    throw new IllegalStateException(
                            "GitHub archive download failed. Status=" + statusCode + " path=" + apiPath
                    );
                }

                return tempFile;
            } catch (GitHubRateLimitException exception) {
                throw exception;
            } catch (IOException exception) {
                lastFailure = new IllegalStateException(
                        "Unable to download GitHub archive for ref " + ref,
                        exception
                );
                sleepBeforeRetry(attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "GitHub archive download interrupted for ref " + ref,
                        exception
                );
            }
        }

        throw new IllegalStateException(
                "GitHub archive download failed after retries for ref " + ref,
                lastFailure
        );
    }

    private boolean isRetryableStatus(final int statusCode) {
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private boolean isRateLimitExceeded(final String responseBody) {
        return responseBody != null
                && responseBody.toLowerCase().contains("rate limit exceeded");
    }

    private String buildRateLimitMessage() {
        return """
                GitHub API rate limit exceeded.
                Add a GitHub token in the GITHUB_TOKEN environment variable in your IntelliJ Run Configuration.
                Example token type: fine-grained personal access token with read-only access to public repositories.
                """;
    }

    private void sleepBeforeRetry(final int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            return;
        }

        try {
            Thread.sleep(RETRY_SLEEP_MILLIS * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry sleep interrupted", exception);
        }
    }

    private HttpRequest.Builder baseRequest(final String apiPath) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + apiPath))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", apiVersion)
                .header("User-Agent", "ReleaseSnapshotApplication");

        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        return builder;
    }

    public static final class GitHubNotFoundException extends RuntimeException {
        public GitHubNotFoundException(final String message) {
            super(message);
        }
    }

    public static final class GitHubRateLimitException extends RuntimeException {
        public GitHubRateLimitException(final String message) {
            super(message);
        }
    }
}