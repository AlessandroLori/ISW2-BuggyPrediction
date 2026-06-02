package it.university.avro.refactoringdataset.sonar;

import java.nio.file.Path;
import java.util.Objects;

public record SonarAnalysisConfiguration(
        boolean autoAnalyze,
        boolean waitForProcessing,
        String scannerCommand,
        Path projectBaseDirectory,
        String projectName,
        String organization,
        String sources,
        String tests,
        String javaBinaries,
        String exclusions,
        int waitTimeoutSeconds,
        int pollIntervalSeconds
) {
    public SonarAnalysisConfiguration {
        scannerCommand = normalize(scannerCommand, "sonar-scanner");
        projectBaseDirectory = Objects.requireNonNull(projectBaseDirectory, "projectBaseDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        projectName = normalize(projectName, "refactoring-analysis");
        organization = normalize(organization, "");
        sources = normalize(sources, "lang/java/avro/src/main/java,saves");
        tests = normalize(tests, "lang/java/avro/src/test/java");
        javaBinaries = normalize(javaBinaries, "lang/java/avro/target/classes");
        exclusions = normalize(exclusions, "**/target/**");
        if (waitTimeoutSeconds <= 0) {
            waitTimeoutSeconds = 600;
        }
        if (pollIntervalSeconds <= 0) {
            pollIntervalSeconds = 5;
        }
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
