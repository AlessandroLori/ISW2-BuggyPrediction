package it.university.avro.exporter.iv.config;

import java.nio.file.Path;
import java.util.Objects;

public record InjectedVersionConfiguration(
        Path inputCsvPath,
        Path outputCsvPath
) {

    public InjectedVersionConfiguration {
        inputCsvPath = Objects.requireNonNull(inputCsvPath, "inputCsvPath must not be null");
        outputCsvPath = Objects.requireNonNull(outputCsvPath, "outputCsvPath must not be null");
    }

    public static InjectedVersionConfiguration defaultConfiguration() {
        return new InjectedVersionConfiguration(
                Path.of("TicketDetails.csv"),
                Path.of("TicketDetailsWithIV.csv")
        );
    }
}