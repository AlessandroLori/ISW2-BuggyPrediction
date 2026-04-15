package it.university.avro.metrics.service;

import it.university.avro.metrics.csv.ReleaseClassInventoryReader;
import it.university.avro.metrics.csv.ReleaseMetricsCsvWriter;
import it.university.avro.metrics.csv.TicketDetailsBugIdReader;
import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.domain.InventoryRecord;
import it.university.avro.metrics.domain.ReleaseMetricsRecord;
import it.university.avro.metrics.domain.StaticMetrics;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.metrics.history.GitHistoryMetricExtractor;
import it.university.avro.metrics.snapshot.JavaLineMetricExtractor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ReleaseMetricsGenerationService {

    private final ReleaseClassInventoryReader inventoryReader;
    private final TicketDetailsBugIdReader bugIdReader;
    private final ReleaseMetricsCsvWriter csvWriter;
    private final JavaLineMetricExtractor staticMetricExtractor;
    private final GitHistoryMetricExtractor historyMetricExtractor;

    public ReleaseMetricsGenerationService(
            final ReleaseClassInventoryReader inventoryReader,
            final TicketDetailsBugIdReader bugIdReader,
            final ReleaseMetricsCsvWriter csvWriter,
            final JavaLineMetricExtractor staticMetricExtractor,
            final GitHistoryMetricExtractor historyMetricExtractor
    ) {
        this.inventoryReader = inventoryReader;
        this.bugIdReader = bugIdReader;
        this.csvWriter = csvWriter;
        this.staticMetricExtractor = staticMetricExtractor;
        this.historyMetricExtractor = historyMetricExtractor;
    }

    public void generate(
            final Path inventoryCsvPath,
            final Path ticketDetailsCsvPath,
            final Path outputCsvPath,
            final String repositoryUrl
    ) {
        final List<InventoryRecord> inventoryRecords = inventoryReader.read(inventoryCsvPath);
        final Set<String> bugIds = bugIdReader.readBugIds(ticketDetailsCsvPath);

        final List<ReleaseMetricsRecord> outputRecords = new ArrayList<>();

        try (TemporaryGitRepository repository = TemporaryGitRepository.cloneRepository(repositoryUrl)) {
            String currentVersion = null;
            String currentTag = null;

            for (InventoryRecord inventoryRecord : inventoryRecords) {
                if (!inventoryRecord.version().equals(currentVersion)) {
                    final String resolvedVersion = inventoryRecord.version();
                    currentVersion = resolvedVersion;

                    currentTag = repository.resolveTag(resolvedVersion)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Unable to resolve git tag for version " + resolvedVersion
                            ));

                    System.out.println("Processing release " + currentVersion + " with tag " + currentTag);
                }

                final String source = repository.readFileAtTag(currentTag, inventoryRecord.classPath()).orElse("");
                final StaticMetrics staticMetrics = source.isBlank()
                        ? StaticMetrics.empty()
                        : staticMetricExtractor.extract(source);

                final HistoryMetrics historyMetrics = historyMetricExtractor.extract(
                        repository,
                        currentTag,
                        inventoryRecord.classPath(),
                        bugIds
                );

                outputRecords.add(new ReleaseMetricsRecord(
                        inventoryRecord.version(),
                        inventoryRecord.classPath(),
                        staticMetrics.loc(),
                        historyMetrics.locTouched(),
                        historyMetrics.revs(),
                        historyMetrics.fixes(),
                        historyMetrics.auth(),
                        historyMetrics.locAdded(),
                        historyMetrics.maxLocAdded(),
                        historyMetrics.avgLocAdded(),
                        historyMetrics.churn(),
                        historyMetrics.maxChurn(),
                        historyMetrics.avgChurn(),
                        staticMetrics.commentLines(),
                        "",
                        "NO"
                ));
            }
        }

        csvWriter.write(outputRecords);
        System.out.println("Generated metrics csv: " + outputCsvPath + " | rows=" + outputRecords.size());
    }
}