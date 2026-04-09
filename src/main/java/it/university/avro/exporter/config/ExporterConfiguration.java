package it.university.avro.exporter.config;

import java.nio.file.Path;
import java.util.Objects;

public record ExporterConfiguration(
        String baseUrl,
        String projectKey,
        int searchPageSize,
        Path outputCsvPath
) {

    public ExporterConfiguration {
        validateBaseUrl(baseUrl);
        validateProjectKey(projectKey);
        validateSearchPageSize(searchPageSize);
        outputCsvPath = Objects.requireNonNull(outputCsvPath, "outputCsvPath must not be null");
    }

    public static ExporterConfiguration defaultConfiguration() {
        return new ExporterConfiguration(
                "https://issues.apache.org/jira",
                "AVRO",
                100,
                Path.of("TicketDetails.csv")
        );
    }

    private static void validateBaseUrl(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
    }

    private static void validateProjectKey(final String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("projectKey must not be blank");
        }
    }

    private static void validateSearchPageSize(final int searchPageSize) {
        if (searchPageSize <= 0) {
            throw new IllegalArgumentException("searchPageSize must be greater than zero");
        }
    }
}
