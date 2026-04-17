package it.university.avro.metrics.config;

import java.nio.file.Path;

public record MetricsConfiguration(
        Path inventoryCsvPath,
        Path ticketDetailsCsvPath,
        Path outputCsvPath,
        String repositoryUrl
) {
    public static MetricsConfiguration defaultConfiguration() {
        return new MetricsConfiguration(
                Path.of("ReleaseClassInventory.csv"),
                Path.of("TicketDetailsWithIV.csv"),
                Path.of("output", "ReleaseMetrics.csv"),
                "https://github.com/apache/avro.git"
        );
    }
}