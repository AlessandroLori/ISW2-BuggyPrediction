package it.university.avro.metrics;

import it.university.avro.metrics.config.MetricsConfiguration;
import it.university.avro.metrics.csv.ReleaseClassInventoryReader;
import it.university.avro.metrics.csv.ReleaseMetricsCsvWriter;
import it.university.avro.metrics.csv.TicketDetailsBugIdReader;
import it.university.avro.metrics.history.GitHistoryMetricExtractor;
import it.university.avro.metrics.service.ReleaseMetricsGenerationService;
import it.university.avro.metrics.snapshot.JavaLineMetricExtractor;

public final class ReleaseMetricsApplication {

    private ReleaseMetricsApplication() {
    }

    public static void main(final String[] args) {
        final MetricsConfiguration configuration = MetricsConfiguration.defaultConfiguration();

        final ReleaseMetricsGenerationService service = new ReleaseMetricsGenerationService(
                new ReleaseClassInventoryReader(),
                new TicketDetailsBugIdReader(),
                new ReleaseMetricsCsvWriter(configuration.outputCsvPath()),
                new JavaLineMetricExtractor(),
                new GitHistoryMetricExtractor()
        );

        service.generate(
                configuration.inventoryCsvPath(),
                configuration.ticketDetailsCsvPath(),
                configuration.outputCsvPath(),
                configuration.repositoryUrl()
        );
    }
}