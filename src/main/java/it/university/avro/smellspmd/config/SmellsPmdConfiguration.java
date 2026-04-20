package it.university.avro.smellspmd.config;

import java.nio.file.Path;

public record SmellsPmdConfiguration(
        Path inputMetricsCsvPath,
        Path outputMetricsCsvPath,
        String repositoryUrl,
        String pmdRulesetPath
) {
    public static SmellsPmdConfiguration defaultConfiguration() {
        return new SmellsPmdConfiguration(
                Path.of("output", "ReleaseMetrics.csv"),
                Path.of("output", "ReleaseMetrics.csv"),
                "https://github.com/apache/avro.git",
                "rulesets/java/quickstart.xml"
        );
    }
}
