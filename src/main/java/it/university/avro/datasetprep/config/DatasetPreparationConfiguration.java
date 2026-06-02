package it.university.avro.datasetprep.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record DatasetPreparationConfiguration(
        Path inputCsvPath,
        Path standardizedOutputCsvPath,
        Path shuffledOutputCsvPath,
        long randomSeed,
        Set<String> excludedColumns
) {

    private static final String OUTPUT_DIRECTORY = "output";

    public DatasetPreparationConfiguration {
        if (inputCsvPath == null) {
            throw new IllegalArgumentException("inputCsvPath must not be null");
        }
        if (standardizedOutputCsvPath == null) {
            throw new IllegalArgumentException("standardizedOutputCsvPath must not be null");
        }
        if (shuffledOutputCsvPath == null) {
            throw new IllegalArgumentException("shuffledOutputCsvPath must not be null");
        }
        if (excludedColumns == null) {
            throw new IllegalArgumentException("excludedColumns must not be null");
        }

        excludedColumns = excludedColumns.stream()
                .map(column -> column.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static DatasetPreparationConfiguration defaultConfiguration() {
        return new DatasetPreparationConfiguration(
                Path.of(OUTPUT_DIRECTORY, "ReleaseMetrics.csv"),
                Path.of(OUTPUT_DIRECTORY, "Dataset.csv"),
                Path.of(OUTPUT_DIRECTORY, "Dataset_shuffled.csv"),
                42L,
                Set.of("version", "classpath", "buggy")
        );
    }
}
