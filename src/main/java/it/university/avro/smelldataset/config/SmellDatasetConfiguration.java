package it.university.avro.smelldataset.config;

import java.nio.file.Path;
import java.util.Objects;

public record SmellDatasetConfiguration(
        Path outputCsvPath,
        Path ticketDetailsWithIvCsvPath,
        String repositoryUrl,
        String owner,
        String repository,
        String gitHubToken,
        String gitHubApiVersion,
        String pmdRulesetPath
) {

    private static final Path DEFAULT_OUTPUT = Path.of("output", "smelldataset.csv");
    private static final Path DEFAULT_TICKET_DETAILS_WITH_IV = Path.of("TicketDetailsWithIV.csv");
    private static final String DEFAULT_REPOSITORY_URL = "https://github.com/apache/avro.git";
    private static final String DEFAULT_OWNER = "apache";
    private static final String DEFAULT_REPOSITORY = "avro";
    private static final String DEFAULT_GITHUB_API_VERSION = "2026-03-10";
    private static final String DEFAULT_PMD_RULESET = "rulesets/java/quickstart.xml";

    public SmellDatasetConfiguration {
        outputCsvPath = Objects.requireNonNull(outputCsvPath, "outputCsvPath must not be null");
        ticketDetailsWithIvCsvPath = Objects.requireNonNull(
                ticketDetailsWithIvCsvPath,
                "ticketDetailsWithIvCsvPath must not be null"
        );
        repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl must not be null");
        owner = Objects.requireNonNull(owner, "owner must not be null");
        repository = Objects.requireNonNull(repository, "repository must not be null");
        gitHubApiVersion = Objects.requireNonNull(gitHubApiVersion, "gitHubApiVersion must not be null");
        pmdRulesetPath = Objects.requireNonNull(pmdRulesetPath, "pmdRulesetPath must not be null");
        gitHubToken = gitHubToken == null ? "" : gitHubToken.trim();
    }

    public static SmellDatasetConfiguration fromArgs(final String[] args) {
        if (args.length == 0) {
            return defaultConfiguration();
        }

        if (args.length == 1) {
            return defaultConfiguration(Path.of(args[0]));
        }

        if (args.length == 2) {
            return defaultConfiguration(Path.of(args[0]), args[1]);
        }

        throw new IllegalArgumentException(
                "Usage: SmellDatasetApplication [outputCsvPath] [pmdRulesetPath]"
        );
    }

    private static SmellDatasetConfiguration defaultConfiguration() {
        return defaultConfiguration(DEFAULT_OUTPUT, DEFAULT_PMD_RULESET);
    }

    private static SmellDatasetConfiguration defaultConfiguration(final Path outputCsvPath) {
        return defaultConfiguration(outputCsvPath, DEFAULT_PMD_RULESET);
    }

    private static SmellDatasetConfiguration defaultConfiguration(
            final Path outputCsvPath,
            final String pmdRulesetPath
    ) {
        return new SmellDatasetConfiguration(
                outputCsvPath,
                DEFAULT_TICKET_DETAILS_WITH_IV,
                DEFAULT_REPOSITORY_URL,
                DEFAULT_OWNER,
                DEFAULT_REPOSITORY,
                System.getenv("GITHUB_TOKEN"),
                DEFAULT_GITHUB_API_VERSION,
                pmdRulesetPath
        );
    }
}
