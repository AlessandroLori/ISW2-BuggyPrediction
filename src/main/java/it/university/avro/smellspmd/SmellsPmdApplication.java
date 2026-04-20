package it.university.avro.smellspmd;

import it.university.avro.metrics.csv.ReleaseMetricsCsvWriter;
import it.university.avro.smellspmd.config.SmellsPmdConfiguration;
import it.university.avro.smellspmd.csv.ReleaseMetricsCsvReader;
import it.university.avro.smellspmd.pmd.PmdJavaSmellAnalyzer;
import it.university.avro.smellspmd.service.ReleaseSmellsPmdGenerationService;
import it.university.avro.smellspmd.source.ReleaseSourceSnapshotBuilder;

public final class SmellsPmdApplication {

    private SmellsPmdApplication() {
    }

    public static void main(final String[] args) {
        final SmellsPmdConfiguration configuration = SmellsPmdConfiguration.defaultConfiguration();

        final ReleaseSmellsPmdGenerationService service = new ReleaseSmellsPmdGenerationService(
                new ReleaseMetricsCsvReader(),
                new ReleaseMetricsCsvWriter(configuration.outputMetricsCsvPath()),
                new ReleaseSourceSnapshotBuilder(),
                new PmdJavaSmellAnalyzer()
        );

        service.generate(
                configuration.inputMetricsCsvPath(),
                configuration.outputMetricsCsvPath(),
                configuration.repositoryUrl(),
                configuration.pmdRulesetPath()
        );
    }
}
