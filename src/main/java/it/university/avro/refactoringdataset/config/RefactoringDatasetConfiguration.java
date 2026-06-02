package it.university.avro.refactoringdataset.config;

import java.nio.file.Path;
import java.util.List;

public record RefactoringDatasetConfiguration(
        List<Path> productionSourcePaths,
        Path refactoringSavesDirectory,
        int refactoringVariantCount,
        Path outputCsvPath,
        String pmdRulesetPath
) {
    private static final Path DEFAULT_AVRO_PROJECT_ROOT = Path.of("/home/reliq/Documents/ISW2/2025-2026/DeaProg");
    private static final int DEFAULT_REFACTORING_VARIANT_COUNT = 4;

    public RefactoringDatasetConfiguration {
        productionSourcePaths = List.copyOf(productionSourcePaths);
        if (refactoringVariantCount < 0) {
            throw new IllegalArgumentException("refactoringVariantCount must be greater than or equal to zero");
        }
    }

    public static RefactoringDatasetConfiguration defaultConfiguration() {
        Path avroProjectRoot = readPath("AVRO_PROJECT_ROOT", DEFAULT_AVRO_PROJECT_ROOT);
        return new RefactoringDatasetConfiguration(
                List.of(
                        avroProjectRoot.resolve("lang/java/avro/src/main/java/org/apache/avro/Schema.java"),
                        avroProjectRoot.resolve("lang/java/avro/src/main/java/org/apache/avro/file/DataFileWriter.java")
                ),
                readPath("REFACTORING_SAVES_DIR", avroProjectRoot.resolve("saves")),
                readInt("REFACTORING_VARIANT_COUNT", DEFAULT_REFACTORING_VARIANT_COUNT),
                Path.of("output", "RefactoringClassMetrics.csv"),
                "rulesets/java/quickstart.xml"
        );
    }

    private static Path readPath(String key, Path defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Path.of(value.trim());
    }

    private static int readInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer environment variable " + key + ": " + value, exception);
        }
    }
}
