package it.university.avro.weka.config;

import java.nio.file.Path;

public record WekaFeatureSelectionConfiguration(
        Path inputCsvPath,
        Path outputDirectory,
        int wrapperInternalFolds,
        int wrapperSeed,
        int outerCrossValidationFolds,
        int outerCrossValidationSeed
) {
    public static WekaFeatureSelectionConfiguration defaultConfiguration() {
        return new WekaFeatureSelectionConfiguration(
                Path.of("output", "ReleaseMetricsCutted_train.csv"),
                Path.of("output", "FeatureSelectionOutput"),
                5,
                1,
                10,
                1
        );
    }
}
