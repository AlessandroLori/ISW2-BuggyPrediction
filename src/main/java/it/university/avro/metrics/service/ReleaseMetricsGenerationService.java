package it.university.avro.metrics.service;

import it.university.avro.exporter.iv.service.VersionNameComparator;
import it.university.avro.metrics.csv.ReleaseClassInventoryReader;
import it.university.avro.metrics.csv.ReleaseMetricsCsvWriter;
import it.university.avro.metrics.csv.TicketDetailsBugIdReader;
import it.university.avro.metrics.domain.BugTicket;
import it.university.avro.metrics.domain.InventoryRecord;
import it.university.avro.metrics.domain.ReleaseMetricsRecord;
import it.university.avro.metrics.domain.StaticMetrics;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.metrics.history.BuggyClassLabelResolver;
import it.university.avro.metrics.history.GitHistoryMetricExtractor;
import it.university.avro.metrics.history.HistoryExtractionResult;
import it.university.avro.metrics.snapshot.JavaLineMetricExtractor;
import it.university.avro.metrics.snapshot.JavaSourceLocator;
import it.university.avro.metrics.snapshot.SourceLookupResult;
import it.university.avro.metrics.util.ClassPathNormalizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReleaseMetricsGenerationService {

    private final ReleaseClassInventoryReader inventoryReader;
    private final TicketDetailsBugIdReader bugIdReader;
    private final ReleaseMetricsCsvWriter csvWriter;
    private final JavaLineMetricExtractor staticMetricExtractor;
    private final GitHistoryMetricExtractor historyMetricExtractor;
    private final JavaSourceLocator sourceLocator;
    private final BuggyClassLabelResolver buggyClassLabelResolver;
    private final VersionNameComparator versionComparator;

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
        this.buggyClassLabelResolver = new BuggyClassLabelResolver();
        this.versionComparator = new VersionNameComparator();
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
            final Map<String, Set<String>> touchedClassesByTicket = buggyClassLabelResolver
                    .resolveTouchedClassesByTicket(repository, tickets);
            final Map<String, Set<String>> buggyClassesByVersion = buildBuggyClassesByVersion(
                    inventoryRecords,
                    tickets,
                    touchedClassesByTicket
            );

            logBuggyLabelSummary(tickets, touchedClassesByTicket, buggyClassesByVersion);

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

                final String effectivePath = sourceLookup.resolvedPath();
                final StaticMetrics staticMetrics = sourceLookup.found()
                        ? staticMetricExtractor.extract(sourceLookup.sourceCode())
                        : StaticMetrics.empty();

                final HistoryExtractionResult historyResult = historyMetricExtractor.extract(
                        repository,
                        currentWindowStartTag,
                        currentTag,
                        effectivePath,
                        tickets
                );

                if (!sourceLookup.found()) {
                    System.out.println(
                            "[STATIC-SUSPECT] release=" + inventoryRecord.version()
                                    + " | path=" + inventoryRecord.classPath()
                                    + " | reason=source_not_found_at_release_tag"
                    );
                } else if (!sourceLookup.exactMatch()) {
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
                    } else if (!historyResult.hasWindowCommits() && !historyResult.hasCumulativeCommits() && sourceLookup.found()) {
                        System.out.println(
                                "[ZERO-SUSPECT] release=" + inventoryRecord.version()
                                        + " | path=" + inventoryRecord.classPath()
                                        + " | resolved=" + effectivePath
                                        + " | reason=file_exists_but_no_history_linked"
                        );
                    }
                }

                final String normalizedClassPath = ClassPathNormalizer.normalize(inventoryRecord.classPath());
                final String buggy = buggyClassesByVersion
                        .getOrDefault(inventoryRecord.version(), Set.of())
                        .contains(normalizedClassPath)
                        ? "YES"
                        : "NO";

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
                        buggy
                ));
            }
        }

        csvWriter.write(outputRecords);
        System.out.println("Generated metrics csv: " + outputCsvPath + " | rows=" + outputRecords.size());
    }

    private Map<String, Set<String>> buildBuggyClassesByVersion(
            final List<InventoryRecord> inventoryRecords,
            final Map<String, BugTicket> tickets,
            final Map<String, Set<String>> touchedClassesByTicket
    ) {
        final Set<String> releaseVersions = new LinkedHashSet<>();
        for (InventoryRecord inventoryRecord : inventoryRecords) {
            releaseVersions.add(inventoryRecord.version());
        }

        final Map<String, Set<String>> buggyClassesByVersion = new LinkedHashMap<>();
        for (String releaseVersion : releaseVersions) {
            buggyClassesByVersion.put(releaseVersion, new LinkedHashSet<>());
        }

        for (Map.Entry<String, Set<String>> entry : touchedClassesByTicket.entrySet()) {
            final BugTicket ticket = tickets.get(entry.getKey());
            if (ticket == null || !ticket.hasInjectedVersion() || !ticket.hasFixedVersion()) {
                continue;
            }

            for (String releaseVersion : releaseVersions) {
                if (isWithinBuggyWindow(releaseVersion, ticket.injectedVersion(), ticket.fixedVersion())) {
                    buggyClassesByVersion.get(releaseVersion).addAll(entry.getValue());
                }
            }
        }

        final Map<String, Set<String>> immutableMap = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : buggyClassesByVersion.entrySet()) {
            immutableMap.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutableMap);
    }

    private boolean isWithinBuggyWindow(
            final String releaseVersion,
            final String injectedVersion,
            final String fixedVersion
    ) {
        return versionComparator.compare(releaseVersion, injectedVersion) >= 0
                && versionComparator.compare(releaseVersion, fixedVersion) < 0;
    }

    private void logBuggyLabelSummary(
            final Map<String, BugTicket> tickets,
            final Map<String, Set<String>> touchedClassesByTicket,
            final Map<String, Set<String>> buggyClassesByVersion
    ) {
        long ticketsWithResolvedClasses = 0;
        for (String ticketId : tickets.keySet()) {
            if (touchedClassesByTicket.containsKey(ticketId) && !touchedClassesByTicket.get(ticketId).isEmpty()) {
                ticketsWithResolvedClasses++;
            }
        }

        int totalBuggyVersionClassBindings = 0;
        for (Set<String> classes : buggyClassesByVersion.values()) {
            totalBuggyVersionClassBindings += classes.size();
        }

        System.out.println(
                "[BUGGY-LABELS] ticketsLoaded=" + tickets.size()
                        + " | ticketsWithMatchedCommits=" + ticketsWithResolvedClasses
                        + " | versionsWithBuggyClasses=" + buggyClassesByVersion.size()
                        + " | versionClassBindings=" + totalBuggyVersionClassBindings
        );
    }
}