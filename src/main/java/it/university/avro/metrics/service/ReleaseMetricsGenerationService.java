package it.university.avro.metrics.service;

import it.university.avro.metrics.csv.ReleaseClassInventoryReader;
import it.university.avro.metrics.csv.ReleaseMetricsCsvWriter;
import it.university.avro.metrics.csv.TicketDetailsBugIdReader;
import it.university.avro.metrics.domain.BugTicket;
import it.university.avro.metrics.domain.InventoryRecord;
import it.university.avro.metrics.domain.ReleaseMetricsRecord;
import it.university.avro.metrics.domain.StaticMetrics;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.metrics.history.GitHistoryMetricExtractor;
import it.university.avro.metrics.history.HistoryExtractionResult;
import it.university.avro.metrics.snapshot.JavaLineMetricExtractor;
import it.university.avro.metrics.snapshot.JavaSourceLocator;
import it.university.avro.metrics.snapshot.SourceLookupResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReleaseMetricsGenerationService {

    private final ReleaseClassInventoryReader inventoryReader;
    private final TicketDetailsBugIdReader bugIdReader;
    private final ReleaseMetricsCsvWriter csvWriter;
    private final JavaLineMetricExtractor staticMetricExtractor;
    private final GitHistoryMetricExtractor historyMetricExtractor;
    private final JavaSourceLocator sourceLocator;

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
        this.sourceLocator = new JavaSourceLocator();
    }

    public void generate(
            final Path inventoryCsvPath,
            final Path ticketDetailsCsvPath,
            final Path outputCsvPath,
            final String repositoryUrl
    ) {
        final List<InventoryRecord> inventoryRecords = inventoryReader.read(inventoryCsvPath);
        final Map<String, BugTicket> tickets = bugIdReader.readTickets(ticketDetailsCsvPath);

        final List<ReleaseMetricsRecord> outputRecords = new ArrayList<>();

        try (TemporaryGitRepository repository = TemporaryGitRepository.cloneRepository(repositoryUrl)) {
            String currentVersion = null;
            String currentTag = null;
            String currentWindowStartTag = null;
            String lastResolvedTag = null;

            for (InventoryRecord inventoryRecord : inventoryRecords) {
                if (!inventoryRecord.version().equals(currentVersion)) {
                    final String resolvedVersion = inventoryRecord.version();
                    currentVersion = resolvedVersion;

                    currentWindowStartTag = lastResolvedTag;

                    currentTag = repository.resolveTag(resolvedVersion)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Unable to resolve git tag for version " + resolvedVersion
                            ));

                    lastResolvedTag = currentTag;

                    System.out.println(
                            "Processing release " + currentVersion
                                    + " with tag " + currentTag
                                    + " | previousTag=" + currentWindowStartTag
                    );
                }

                final SourceLookupResult sourceLookup = sourceLocator.locate(
                        repository,
                        currentTag,
                        inventoryRecord.classPath()
                );

                if (!sourceLookup.found()) {
                    System.out.println(
                            "[DROP-STATIC-SUSPECT] release=" + inventoryRecord.version()
                                    + " | path=" + inventoryRecord.classPath()
                                    + " | reason=source_not_found_at_release_tag"
                    );
                    continue;
                }

                final String effectivePath = sourceLookup.resolvedPath();
                final StaticMetrics staticMetrics = staticMetricExtractor.extract(sourceLookup.sourceCode());

                final HistoryExtractionResult historyResult = historyMetricExtractor.extract(
                        repository,
                        currentWindowStartTag,
                        currentTag,
                        effectivePath,
                        tickets
                );

                if (!sourceLookup.exactMatch()) {
                    System.out.println(
                            "[PATH-RECOVERED] release=" + inventoryRecord.version()
                                    + " | requested=" + inventoryRecord.classPath()
                                    + " | resolved=" + sourceLookup.resolvedPath()
                    );
                }

                if (historyResult.metrics().revs() == 0
                        && historyResult.metrics().auth() == 0
                        && historyResult.metrics().locTouched() == 0
                        && historyResult.metrics().locAdded() == 0
                        && historyResult.metrics().churn() == 0) {

                    if (!historyResult.hasWindowCommits() && historyResult.hasCumulativeCommits()) {
                        System.out.println(
                                "[ZERO-OK] release=" + inventoryRecord.version()
                                        + " | path=" + inventoryRecord.classPath()
                                        + " | reason=no_commits_in_release_window"
                        );
                    } else if (!historyResult.hasWindowCommits()
                            && !historyResult.hasCumulativeCommits()) {
                        System.out.println(
                                "[ZERO-SUSPECT] release=" + inventoryRecord.version()
                                        + " | path=" + inventoryRecord.classPath()
                                        + " | resolved=" + effectivePath
                                        + " | reason=file_exists_but_no_history_linked"
                        );
                    }
                }

                assertNoStaticHistoryMismatch(
                        inventoryRecord.version(),
                        inventoryRecord.classPath(),
                        staticMetrics,
                        historyResult
                );

                outputRecords.add(new ReleaseMetricsRecord(
                        inventoryRecord.version(),
                        inventoryRecord.classPath(),
                        staticMetrics.loc(),
                        historyResult.metrics().locTouched(),
                        historyResult.metrics().revs(),
                        historyResult.metrics().fixes(),
                        historyResult.metrics().auth(),
                        historyResult.metrics().locAdded(),
                        historyResult.metrics().maxLocAdded(),
                        historyResult.metrics().avgLocAdded(),
                        historyResult.metrics().churn(),
                        historyResult.metrics().maxChurn(),
                        historyResult.metrics().avgChurn(),
                        staticMetrics.commentLines(),
                        "",
                        "NO"
                ));
            }
        }

        csvWriter.write(outputRecords);
        System.out.println("Generated metrics csv: " + outputCsvPath + " | rows=" + outputRecords.size());
    }

    private void assertNoStaticHistoryMismatch(
            final String version,
            final String classPath,
            final StaticMetrics staticMetrics,
            final HistoryExtractionResult historyResult
    ) {
        final boolean hasReleaseWindowMetrics =
                historyResult.metrics().revs() > 0
                        || historyResult.metrics().auth() > 0
                        || historyResult.metrics().locTouched() > 0
                        || historyResult.metrics().locAdded() > 0
                        || historyResult.metrics().churn() > 0;

        final boolean hasZeroStaticSnapshot =
                staticMetrics.loc() == 0 && staticMetrics.commentLines() == 0;

        if (hasZeroStaticSnapshot && hasReleaseWindowMetrics) {
            throw new IllegalStateException(
                    "Inconsistent metrics for release=" + version
                            + " path=" + classPath
                            + " : static snapshot is zero but release-window metrics are not zero"
            );
        }
    }
}