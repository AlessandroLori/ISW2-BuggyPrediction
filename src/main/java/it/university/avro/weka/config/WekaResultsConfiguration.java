package it.university.avro.weka.config;

import java.nio.file.Path;

public record WekaResultsConfiguration(
        Path inputCsvPath,
        Path outputCsvPath
) {
    public static WekaResultsConfiguration defaultConfiguration() {
        return new WekaResultsConfiguration(
                Path.of("output", "WekaResults.csv"),
                Path.of("output", "FinalWekaResults.csv")
        );
    }
}
