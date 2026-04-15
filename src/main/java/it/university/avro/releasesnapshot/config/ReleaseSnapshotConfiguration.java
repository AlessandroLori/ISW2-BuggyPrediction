package it.university.avro.releasesnapshot.config;

import java.nio.file.Path;
import java.util.Objects;

public record ReleaseSnapshotConfiguration(
        String owner,
        String repository,
        Path ticketDetailsCsvPath,
        Path outputCsvPath,
        String gitHubToken,
        String gitHubApiVersion
) {

    public ReleaseSnapshotConfiguration {
        owner = Objects.requireNonNull(owner, "owner must not be null");
        repository = Objects.requireNonNull(repository, "repository must not be null");
        ticketDetailsCsvPath = Objects.requireNonNull(ticketDetailsCsvPath, "ticketDetailsCsvPath must not be null");
        outputCsvPath = Objects.requireNonNull(outputCsvPath, "outputCsvPath must not be null");
        gitHubApiVersion = Objects.requireNonNull(gitHubApiVersion, "gitHubApiVersion must not be null");
        gitHubToken = gitHubToken == null ? "" : gitHubToken.trim();
    }

    public static ReleaseSnapshotConfiguration defaultConfiguration() {
        return new ReleaseSnapshotConfiguration(
                "apache",
                "avro",
                Path.of("TicketDetails.csv"),
                Path.of("ReleaseClassInventory.csv"),
                System.getenv("GITHUB_TOKEN"),
                "2026-03-10"
        );
    }
}