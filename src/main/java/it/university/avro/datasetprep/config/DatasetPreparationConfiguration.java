package it.university.avro.datasetprep.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record DatasetPreparationConfiguration(
        Path inputCsvPath,
        Path trainingOutputCsvPath,
        Path testingOutputCsvPath,
        double testRatio,
        long randomSeed,
        Set<String> excludedColumns
) {

    public DatasetPreparationConfiguration {
        if (inputCsvPath == null) {
            throw new IllegalArgumentException("inputCsvPath must not be null");
        }
        if (trainingOutputCsvPath == null) {
            throw new IllegalArgumentException("trainingOutputCsvPath must not be null");
        }
        if (testingOutputCsvPath == null) {
            throw new IllegalArgumentException("testingOutputCsvPath must not be null");
        }
        if (testRatio <= 0.0 || testRatio >= 1.0) {
            throw new IllegalArgumentException("testRatio must be in the open interval (0, 1)");
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
                Path.of("output", "ReleaseMetrics.csv"),
                Path.of("output", "ReleaseMetrics_train.csv"),
                Path.of("output", "ReleaseMetrics_test.csv"),
                0.20d,
                42L,
                Set.of("version", "classpath", "buggy")
        );
    }
}