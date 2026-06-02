package it.university.avro.smelldataset.service;

import it.university.avro.common.ApplicationLog;

import it.university.avro.metrics.csv.TicketDetailsBugIdReader;
import it.university.avro.metrics.domain.BugTicket;
import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.domain.StaticMetrics;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.metrics.history.GitHistoryMetricExtractor;
import it.university.avro.metrics.history.HistoryExtractionResult;
import it.university.avro.metrics.snapshot.JavaLineMetricExtractor;
import it.university.avro.releasesnapshot.archive.ApacheSourceArchiveDownloader;
import it.university.avro.releasesnapshot.github.GitHubApiClient;
import it.university.avro.releasesnapshot.github.GitHubArchiveDownloader;
import it.university.avro.releasesnapshot.scan.ExtractedJavaType;
import it.university.avro.releasesnapshot.scan.JavaDeclaredTypeExtractor;
import it.university.avro.releasesnapshot.scan.JavaSourceUnit;
import it.university.avro.releasesnapshot.scan.LogicalClassPathResolver;
import it.university.avro.releasesnapshot.scan.ProductionJavaClassFilter;
import it.university.avro.releasesnapshot.scan.ZipJavaFileScanner;
import it.university.avro.smelldataset.config.SmellDatasetConfiguration;
import it.university.avro.smelldataset.csv.SmellDatasetCsvWriter;
import it.university.avro.smelldataset.domain.AvroRelease;
import it.university.avro.smelldataset.domain.ProductionJavaSource;
import it.university.avro.smelldataset.domain.SelectedAvroReleases;
import it.university.avro.smelldataset.domain.SmellDatasetRecord;
import it.university.avro.smelldataset.release.ApacheAvroReleaseCatalogClient;
import it.university.avro.smelldataset.release.LatestOfficialAvroReleaseSelector;
import it.university.avro.smellspmd.domain.PmdClassSmellMetrics;
import it.university.avro.smellspmd.pmd.PmdJavaSmellAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SmellDatasetGenerationService {

    private final SmellDatasetConfiguration configuration;
    private final ApacheAvroReleaseCatalogClient releaseCatalogClient;
    private final LatestOfficialAvroReleaseSelector releaseSelector;
    private final ApacheSourceArchiveDownloader apacheSourceArchiveDownloader;
    private final GitHubArchiveDownloader gitHubArchiveDownloader;
    private final ZipJavaFileScanner zipJavaFileScanner;
    private final JavaDeclaredTypeExtractor declaredTypeExtractor;
    private final ProductionJavaClassFilter productionJavaClassFilter;
    private final JavaLineMetricExtractor lineMetricExtractor;
    private final PmdJavaSmellAnalyzer pmdJavaSmellAnalyzer;
    private final GitHistoryMetricExtractor historyMetricExtractor;
    private final TicketDetailsBugIdReader bugIdReader;
    private final SmellDatasetCsvWriter csvWriter;

    public SmellDatasetGenerationService(final SmellDatasetConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.releaseCatalogClient = new ApacheAvroReleaseCatalogClient();
        this.releaseSelector = new LatestOfficialAvroReleaseSelector();
        this.apacheSourceArchiveDownloader = new ApacheSourceArchiveDownloader();
        this.gitHubArchiveDownloader = new GitHubArchiveDownloader(new GitHubApiClient(
                configuration.owner(),
                configuration.repository(),
                configuration.gitHubToken(),
                configuration.gitHubApiVersion()
        ));
        this.zipJavaFileScanner = new ZipJavaFileScanner();
        this.declaredTypeExtractor = new JavaDeclaredTypeExtractor(new LogicalClassPathResolver());
        this.productionJavaClassFilter = new ProductionJavaClassFilter();
        this.lineMetricExtractor = new JavaLineMetricExtractor();
        this.pmdJavaSmellAnalyzer = new PmdJavaSmellAnalyzer();
        this.historyMetricExtractor = new GitHistoryMetricExtractor();
        this.bugIdReader = new TicketDetailsBugIdReader();
        this.csvWriter = new SmellDatasetCsvWriter();
    }

    public void generate() {
        final List<AvroRelease> officialReleases = releaseCatalogClient.fetchOfficialReleases();
        final SelectedAvroReleases selectedReleases = releaseSelector.select(officialReleases);
        final AvroRelease latestRelease = selectedReleases.latestRelease();
        final AvroRelease previousRelease = selectedReleases.previousRelease();

        ApplicationLog.info("Official Avro releases found: " + officialReleases.size());
        ApplicationLog.info("Latest official Avro release: " + latestRelease.version());
        if (previousRelease != null) {
            ApplicationLog.info("Previous official Avro release: " + previousRelease.version());
        }

        try (TemporaryGitRepository repository = TemporaryGitRepository.cloneRepository(configuration.repositoryUrl())) {
            final String currentTag = resolveRequiredTag(repository, latestRelease.version());
            final String previousTag = previousRelease == null
                    ? null
                    : resolveRequiredTag(repository, previousRelease.version());
            final LocalDate currentReleaseDate = repository.resolveCommitDateForRef(currentTag).orElse(null);
            final Map<String, BugTicket> knownBugTickets = readKnownBugTickets();
            final List<ProductionJavaSource> latestProductionSources = readProductionSources(latestRelease, currentTag);
            final Map<String, PmdClassSmellMetrics> pmdMetricsByClassPath = analyzePmdSmells(latestProductionSources);
            final List<SmellDatasetRecord> records = buildRowsWithSmells(
                    repository,
                    previousTag,
                    currentTag,
                    currentReleaseDate,
                    latestProductionSources,
                    pmdMetricsByClassPath,
                    knownBugTickets
            );

            csvWriter.write(configuration.outputCsvPath(), records);

            ApplicationLog.info("Latest official tag: " + currentTag);
            ApplicationLog.info("Production Java source files: " + latestProductionSources.size());
            ApplicationLog.info("Generated smell dataset: " + configuration.outputCsvPath());
            ApplicationLog.info("Rows with nsmells > 0: " + records.size());
        }
    }

    private Map<String, BugTicket> readKnownBugTickets() {
        if (!Files.exists(configuration.ticketDetailsWithIvCsvPath())) {
            ApplicationLog.info("[BUG-TICKET-WARNING] file not found: "
                    + configuration.ticketDetailsWithIvCsvPath()
                    + " | FIXES will be computed with an empty ticket catalog");
            return Map.of();
        }

        return bugIdReader.readTickets(configuration.ticketDetailsWithIvCsvPath());
    }

    private String resolveRequiredTag(final TemporaryGitRepository repository, final String version) {
        return repository.resolveTag(version)
                .orElseThrow(() -> new IllegalStateException("No Git tag found for Avro version " + version));
    }

    private List<ProductionJavaSource> readProductionSources(
            final AvroRelease release,
            final String tagName
    ) {
        final Path archivePath = apacheSourceArchiveDownloader
                .downloadSourceArchive(release.version())
                .orElseGet(() -> gitHubArchiveDownloader.downloadReleaseArchive(tagName));

        try {
            return extractProductionSources(archivePath);
        } finally {
            deleteQuietly(archivePath);
        }
    }

    private List<ProductionJavaSource> extractProductionSources(final Path archivePath) {
        final List<JavaSourceUnit> javaSources = zipJavaFileScanner.scanJavaFiles(archivePath);
        final Map<String, ProductionJavaSource> acceptedSources = new LinkedHashMap<>();
        int discardedSources = 0;

        for (JavaSourceUnit sourceUnit : javaSources) {
            final String normalizedArchivePath = normalizePath(sourceUnit.archivePath());
            final List<ExtractedJavaType> extractedTypes = declaredTypeExtractor.extract(sourceUnit);

            if (!containsEligibleType(normalizedArchivePath, extractedTypes)) {
                discardedSources++;
                continue;
            }

            acceptedSources.putIfAbsent(
                    normalizedArchivePath,
                    new ProductionJavaSource(normalizedArchivePath, sourceUnit.sourceCode())
            );
        }

        ApplicationLog.info("Java files found in latest source archive: " + javaSources.size());
        ApplicationLog.info("Production Java files accepted: " + acceptedSources.size());
        ApplicationLog.info("Java files discarded: " + discardedSources);

        return acceptedSources.values().stream()
                .sorted(Comparator.comparing(ProductionJavaSource::classPath))
                .toList();
    }

    private boolean containsEligibleType(
            final String normalizedArchivePath,
            final List<ExtractedJavaType> extractedTypes
    ) {
        for (ExtractedJavaType extractedType : extractedTypes) {
            if (productionJavaClassFilter.isEligible(normalizedArchivePath, extractedType.typeName())) {
                return true;
            }
        }

        return false;
    }

    private Map<String, PmdClassSmellMetrics> analyzePmdSmells(final List<ProductionJavaSource> sources) {
        final Map<String, String> sourceByClassPath = new LinkedHashMap<>();
        for (ProductionJavaSource source : sources) {
            sourceByClassPath.put(source.classPath(), source.sourceCode());
        }

        return pmdJavaSmellAnalyzer.analyzeByClassPath(
                sourceByClassPath,
                configuration.pmdRulesetPath()
        );
    }

    private List<SmellDatasetRecord> buildRowsWithSmells(
            final TemporaryGitRepository repository,
            final String previousTag,
            final String currentTag,
            final LocalDate currentReleaseDate,
            final List<ProductionJavaSource> sources,
            final Map<String, PmdClassSmellMetrics> pmdMetricsByClassPath,
            final Map<String, BugTicket> knownBugTickets
    ) {
        final List<SmellDatasetRecord> records = new ArrayList<>();
        final Set<String> seenClassPaths = new LinkedHashSet<>();
        final SmellRowBuildContext context = new SmellRowBuildContext(
                repository,
                previousTag,
                currentTag,
                currentReleaseDate,
                pmdMetricsByClassPath,
                knownBugTickets
        );

        for (ProductionJavaSource source : sources) {
            buildRowIfEligible(context, records, seenClassPaths, source);
        }

        return records.stream()
                .sorted(Comparator.comparing(SmellDatasetRecord::classPath))
                .toList();
    }

    private void buildRowIfEligible(
            final SmellRowBuildContext context,
            final List<SmellDatasetRecord> records,
            final Set<String> seenClassPaths,
            final ProductionJavaSource source
    ) {
        final String classPath = normalizePath(source.classPath());
        if (seenClassPaths.add(classPath)) {
            addSmellyRow(context, records, source, classPath);
        }
    }

    private void addSmellyRow(
            final SmellRowBuildContext context,
            final List<SmellDatasetRecord> records,
            final ProductionJavaSource source,
            final String classPath
    ) {
        final PmdClassSmellMetrics pmdMetrics = context.pmdMetricsByClassPath().getOrDefault(
                classPath,
                PmdClassSmellMetrics.empty()
        );
        if (pmdMetrics.smellCount() > 0) {
            records.add(buildRecordWithHistory(context, source, classPath, pmdMetrics));
        }
    }

    private SmellDatasetRecord buildRecordWithHistory(
            final SmellRowBuildContext context,
            final ProductionJavaSource source,
            final String classPath,
            final PmdClassSmellMetrics pmdMetrics
    ) {
        final StaticMetrics staticMetrics = lineMetricExtractor.extract(source.sourceCode());
        final HistoryExtractionResult historyExtractionResult = historyMetricExtractor.extract(
                context.repository(),
                context.previousTag(),
                context.currentTag(),
                context.currentReleaseDate(),
                classPath,
                context.knownBugTickets()
        );
        return toRecord(classPath, staticMetrics, historyExtractionResult.metrics(), pmdMetrics);
    }

    private record SmellRowBuildContext(
            TemporaryGitRepository repository,
            String previousTag,
            String currentTag,
            LocalDate currentReleaseDate,
            Map<String, PmdClassSmellMetrics> pmdMetricsByClassPath,
            Map<String, BugTicket> knownBugTickets
    ) {
    }

    private SmellDatasetRecord toRecord(
            final String classPath,
            final StaticMetrics staticMetrics,
            final HistoryMetrics historyMetrics,
            final PmdClassSmellMetrics pmdMetrics
    ) {
        return new SmellDatasetRecord(
                classPath,
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
                staticMetrics.nestingDepth(),
                staticMetrics.decisionPoints(),
                pmdMetrics.distinctSmellTypes(),
                pmdMetrics.smellCount()
        );
    }

    private String normalizePath(final String path) {
        return path.replace('\\', '/');
    }

    private void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best-effort cleanup of temporary archives.
        }
    }
}
