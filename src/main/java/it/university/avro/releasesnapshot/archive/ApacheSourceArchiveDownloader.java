package it.university.avro.releasesnapshot.archive;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public final class ApacheSourceArchiveDownloader {

    private static final String APACHE_ARCHIVE_BASE = "https://archive.apache.org/dist/avro/";

    private final HttpClient httpClient;

    public ApacheSourceArchiveDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Optional<Path> downloadSourceArchive(final String version) {
        final String url = APACHE_ARCHIVE_BASE
                + "avro-" + version + "/"
                + "avro-src-" + version + ".tar.gz";

        try {
            final Path tempFile = Files.createTempFile("avro-src-" + version + "-", ".tar.gz");

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "ReleaseSnapshotApplication")
                    .GET()
                    .build();

            final HttpResponse<Path> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(tempFile)
            );

            if (response.statusCode() == 404) {
                Files.deleteIfExists(tempFile);
                return Optional.empty();
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(tempFile);
                throw new IllegalStateException(
                        "Apache source archive download failed. Status="
                                + response.statusCode()
                                + " url=" + url
                );
            }

            return Optional.of(tempFile);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to download Apache source archive for version " + version,
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Apache source archive download interrupted for version " + version,
                    exception
            );
        }
    }
}