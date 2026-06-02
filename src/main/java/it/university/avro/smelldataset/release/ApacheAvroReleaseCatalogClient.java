package it.university.avro.smelldataset.release;

import it.university.avro.common.ApplicationLog;

import it.university.avro.smelldataset.domain.AvroRelease;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApacheAvroReleaseCatalogClient {

    private static final List<String> CATALOG_URLS = List.of(
            "https://downloads.apache.org/avro/",
            "https://archive.apache.org/dist/avro/"
    );

    private static final Pattern RELEASE_DIRECTORY_PATTERN = Pattern.compile(
            "href=\\\"avro-([0-9]+(?:\\.[0-9]+)+)/*\\\"",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient;

    public ApacheAvroReleaseCatalogClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public List<AvroRelease> fetchOfficialReleases() {
        final Set<String> versions = new LinkedHashSet<>();

        for (String catalogUrl : CATALOG_URLS) {
            versions.addAll(fetchVersionsFromCatalog(catalogUrl));
        }

        if (versions.isEmpty()) {
            throw new IllegalStateException("No official Apache Avro releases found from Apache catalogs");
        }

        return versions.stream()
                .filter(this::isStableReleaseVersion)
                .map(AvroRelease::new)
                .toList();
    }

    private Set<String> fetchVersionsFromCatalog(final String catalogUrl) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(catalogUrl))
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "SmellDatasetApplication")
                    .GET()
                    .build();

            final HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                ApplicationLog.info("[AVRO-RELEASE-CATALOG-SKIP] url=" + catalogUrl
                        + " | status=" + response.statusCode());
                return Set.of();
            }

            return parseVersions(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Apache Avro release catalog: " + catalogUrl, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading Apache Avro release catalog", exception);
        }
    }

    private Set<String> parseVersions(final String html) {
        final Set<String> versions = new LinkedHashSet<>();
        final Matcher matcher = RELEASE_DIRECTORY_PATTERN.matcher(html == null ? "" : html);

        while (matcher.find()) {
            versions.add(matcher.group(1).trim());
        }

        return versions;
    }

    private boolean isStableReleaseVersion(final String version) {
        final String normalized = version.toLowerCase(Locale.ROOT);
        return !normalized.contains("alpha")
                && !normalized.contains("beta")
                && !normalized.contains("rc")
                && !normalized.contains("snapshot");
    }
}
