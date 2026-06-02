package it.university.avro.metrics.service;

import it.university.avro.common.ApplicationLog;
import it.university.avro.exporter.iv.service.VersionNameComparator;
import it.university.avro.metrics.csv.ReleaseClassInventoryReader;
import it.university.avro.metrics.csv.ReleaseMetricsCsvWriter;
import it.university.avro.metrics.csv.TicketDetailsBugIdReader;
import it.university.avro.metrics.domain.BugTicket;
import it.university.avro.metrics.domain.HistoryMetrics;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReleaseMetricsGenerationService {

    private static final String PATH_LABEL = " | path=";
    private static final String YES = "YES";
    private static final String NO = "NO";

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
            final Map<String, Set<String>> buggyClassesByVersion = prepareBuggyClassesByVersion(
                    repository,
                    inventoryRecords,
                    tickets
            );
            final ReleaseProcessingContext context = new ReleaseProcessingContext(repository, tickets, buggyClassesByVersion);

            for (InventoryRecord inventoryRecord : inventoryRecords) {
                context.moveToVersion(inventoryRecord.version());
                outputRecords.add(buildMetricsRecord(context, inventoryRecord));
            }
        }

        csvWriter.write(outputRecords);
        ApplicationLog.info("Generated metrics csv: " + outputCsvPath + " | rows=" + outputRecords.size());
    }

    private Map<String, Set<String>> prepareBuggyClassesByVersion(
            final TemporaryGitRepository repository,
            final List<InventoryRecord> inventoryRecords,
            final Map<String, BugTicket> tickets
    ) {
        final Map<String, Set<String>> touchedClassesByTicket = buggyClassLabelResolver.resolveTouchedClassesByTicket(
                repository,
                tickets
        );
        final Map<String, Set<String>> buggyClassesByVersion = buildBuggyClassesByVersion(
                inventoryRecords,
                tickets,
                touchedClassesByTicket
        );
        logBuggyLabelSummary(tickets, touchedClassesByTicket, buggyClassesByVersion);
        return buggyClassesByVersion;
    }

    private ReleaseMetricsRecord buildMetricsRecord(
            final ReleaseProcessingContext context,
            final InventoryRecord inventoryRecord
    ) {
        final SourceLookupResult sourceLookup = sourceLocator.locate(
                context.repository(),
                context.currentTag(),
                inventoryRecord.classPath()
        );
        final StaticMetrics staticMetrics = staticMetrics(sourceLookup);
        final HistoryExtractionResult historyResult = historyMetricExtractor.extract(
                context.repository(),
                context.currentWindowStartTag(),
                context.currentTag(),
                context.currentReleaseDate(),
                sourceLookup.resolvedPath(),
                context.tickets()
        );

        logSourceLookup(inventoryRecord, sourceLookup);
        logZeroHistoryIfNeeded(inventoryRecord, sourceLookup, historyResult);

        return toReleaseMetricsRecord(
                inventoryRecord,
                staticMetrics,
                historyResult.metrics(),
                buggyLabel(context.buggyClassesByVersion(), inventoryRecord)
        );
    }

    private StaticMetrics staticMetrics(final SourceLookupResult sourceLookup) {
        if (!sourceLookup.found()) {
            return StaticMetrics.empty();
        }
        return staticMetricExtractor.extract(sourceLookup.sourceCode());
    }

    private void logSourceLookup(
            final InventoryRecord inventoryRecord,
            final SourceLookupResult sourceLookup
    ) {
        if (!sourceLookup.found()) {
            ApplicationLog.info(
                    "[STATIC-SUSPECT] release=" + inventoryRecord.version()
                            + PATH_LABEL + inventoryRecord.classPath()
                            + " | reason=source_not_found_at_release_tag"
            );
        } else if (!sourceLookup.exactMatch()) {
            ApplicationLog.info(
                    "[PATH-RECOVERED] release=" + inventoryRecord.version()
                            + " | requested=" + inventoryRecord.classPath()
                            + " | resolved=" + sourceLookup.resolvedPath()
            );
        }
    }

    private void logZeroHistoryIfNeeded(
            final InventoryRecord inventoryRecord,
            final SourceLookupResult sourceLookup,
            final HistoryExtractionResult historyResult
    ) {
        if (!isZeroHistory(historyResult.metrics())) {
            return;
        }
        if (!historyResult.hasWindowCommits() && historyResult.hasCumulativeCommits()) {
            ApplicationLog.info(
                    "[ZERO-OK] release=" + inventoryRecord.version()
                            + PATH_LABEL + inventoryRecord.classPath()
                            + " | reason=no_commits_in_release_window"
            );
        } else if (!historyResult.hasWindowCommits() && !historyResult.hasCumulativeCommits() && sourceLookup.found()) {
            ApplicationLog.info(
                    "[ZERO-SUSPECT] release=" + inventoryRecord.version()
                            + PATH_LABEL + inventoryRecord.classPath()
                            + " | resolved=" + sourceLookup.resolvedPath()
                            + " | reason=file_exists_but_no_history_linked"
            );
        }
    }

    private boolean isZeroHistory(final HistoryMetrics metrics) {
        return metrics.revs() == 0
                && metrics.auth() == 0
                && metrics.locTouched() == 0
                && metrics.locAdded() == 0
                && metrics.churn() == 0;
    }

    private ReleaseMetricsRecord toReleaseMetricsRecord(
            final InventoryRecord inventoryRecord,
            final StaticMetrics staticMetrics,
            final HistoryMetrics historyMetrics,
            final String buggy
    ) {
        return new ReleaseMetricsRecord(
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
                historyMetrics.changeSetSize(),
                historyMetrics.maxChangeSet(),
                historyMetrics.avgChangeSet(),
                historyMetrics.age(),
                historyMetrics.weightedAge(),
                staticMetrics.commentLines(),
                "",
                0,
                staticMetrics.nestingDepth(),
                staticMetrics.decisionPoints(),
                buggy
        );
    }

    private String buggyLabel(
            final Map<String, Set<String>> buggyClassesByVersion,
            final InventoryRecord inventoryRecord
    ) {
        final String normalizedClassPath = ClassPathNormalizer.normalize(inventoryRecord.classPath());
        return buggyClassesByVersion
                .getOrDefault(inventoryRecord.version(), Set.of())
                .contains(normalizedClassPath)
                ? YES
                : NO;
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
            if (isUsableBugTicket(ticket)) {
                addBuggyClassesForTicket(releaseVersions, buggyClassesByVersion, ticket, entry.getValue());
            }
        }

        return immutableCopy(buggyClassesByVersion);
    }

    private boolean isUsableBugTicket(final BugTicket ticket) {
        return ticket != null && ticket.hasInjectedVersion() && ticket.hasFixedVersion();
    }

    private void addBuggyClassesForTicket(
            final Set<String> releaseVersions,
            final Map<String, Set<String>> buggyClassesByVersion,
            final BugTicket ticket,
            final Set<String> touchedClasses
    ) {
        for (String releaseVersion : releaseVersions) {
            if (isWithinBuggyWindow(releaseVersion, ticket.injectedVersion(), ticket.fixedVersion())) {
                buggyClassesByVersion.get(releaseVersion).addAll(touchedClasses);
            }
        }
    }

    private Map<String, Set<String>> immutableCopy(final Map<String, Set<String>> source) {
        final Map<String, Set<String>> immutableMap = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
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

        ApplicationLog.info(
                "[BUGGY-LABELS] ticketsLoaded=" + tickets.size()
                        + " | ticketsWithMatchedCommits=" + ticketsWithResolvedClasses
                        + " | versionsWithBuggyClasses=" + buggyClassesByVersion.size()
                        + " | versionClassBindings=" + totalBuggyVersionClassBindings
        );
    }

    private final class ReleaseProcessingContext {

        private final TemporaryGitRepository repository;
        private final Map<String, BugTicket> tickets;
        private final Map<String, Set<String>> buggyClassesByVersion;
        private String currentVersion;
        private String currentTag;
        private String currentWindowStartTag;
        private String lastResolvedTag;
        private LocalDate currentReleaseDate;

        private ReleaseProcessingContext(
                final TemporaryGitRepository repository,
                final Map<String, BugTicket> tickets,
                final Map<String, Set<String>> buggyClassesByVersion
        ) {
            this.repository = repository;
            this.tickets = tickets;
            this.buggyClassesByVersion = buggyClassesByVersion;
        }

        void moveToVersion(final String version) {
            if (!version.equals(currentVersion)) {
                currentVersion = version;
                currentWindowStartTag = lastResolvedTag;
                currentTag = resolveTag(version);
                currentReleaseDate = resolveReleaseDate(currentTag);
                lastResolvedTag = currentTag;
                logCurrentRelease();
            }
        }

        private String resolveTag(final String version) {
            return repository.resolveTag(version)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unable to resolve git tag for version " + version
                    ));
        }

        private LocalDate resolveReleaseDate(final String tag) {
            return repository.resolveCommitDateForRef(tag)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unable to resolve commit date for tag " + tag
                    ));
        }

        private void logCurrentRelease() {
            ApplicationLog.info(
                    "Processing release " + currentVersion
                            + " with tag " + currentTag
                            + " | previousTag=" + currentWindowStartTag
            );
        }

        TemporaryGitRepository repository() {
            return repository;
        }

        Map<String, BugTicket> tickets() {
            return tickets;
        }

        Map<String, Set<String>> buggyClassesByVersion() {
            return buggyClassesByVersion;
        }

        String currentTag() {
            return currentTag;
        }

        String currentWindowStartTag() {
            return currentWindowStartTag;
        }

        LocalDate currentReleaseDate() {
            return currentReleaseDate;
        }
    }
}
