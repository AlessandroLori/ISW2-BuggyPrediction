package it.university.avro.releasesnapshot.service;

import it.university.avro.common.ApplicationLog;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.metrics.snapshot.JavaSourceLocator;
import it.university.avro.metrics.snapshot.SourceLookupResult;
import it.university.avro.releasesnapshot.archive.ApacheSourceArchiveDownloader;
import it.university.avro.releasesnapshot.csv.ReleaseClassInventoryCsvWriter;
import it.university.avro.releasesnapshot.csv.TicketDetailsReleaseCatalogReader;
import it.university.avro.releasesnapshot.domain.JavaClassRecord;
import it.university.avro.releasesnapshot.domain.ReleaseCommitSnapshot;
import it.university.avro.releasesnapshot.domain.ReleaseInfo;
import it.university.avro.releasesnapshot.github.GitHubArchiveDownloader;
import it.university.avro.releasesnapshot.github.GitHubTagResolver;
import it.university.avro.releasesnapshot.scan.ExtractedJavaType;
import it.university.avro.releasesnapshot.scan.JavaDeclaredTypeExtractor;
import it.university.avro.releasesnapshot.scan.JavaSourceUnit;
import it.university.avro.releasesnapshot.scan.ProductionJavaClassFilter;
import it.university.avro.releasesnapshot.scan.ZipJavaFileScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ReleaseClassInventoryService {

    private final TicketDetailsReleaseCatalogReader releaseCatalogReader;
    private final ReleaseSelectionService releaseSelectionService;
    private final GitHubTagResolver gitHubTagResolver;
    private final GitHubArchiveDownloader gitHubArchiveDownloader;
    private final ZipJavaFileScanner zipJavaFileScanner;
    private final JavaDeclaredTypeExtractor javaDeclaredTypeExtractor;
    private final ProductionJavaClassFilter productionJavaClassFilter;
    private final ReleaseClassInventoryCsvWriter csvWriter;
    private final ApacheSourceArchiveDownloader apacheSourceArchiveDownloader;
    private final String repositoryUrl;
    private final JavaSourceLocator sourceLocator;

    public ReleaseClassInventoryService(
            final ReleaseCatalogDependencies catalogDependencies,
            final ReleaseSourceDependencies sourceDependencies,
            final String repositoryUrl
    ) {
        this.releaseCatalogReader = catalogDependencies.releaseCatalogReader();
        this.releaseSelectionService = catalogDependencies.releaseSelectionService();
        this.gitHubTagResolver = catalogDependencies.gitHubTagResolver();
        this.gitHubArchiveDownloader = catalogDependencies.gitHubArchiveDownloader();
        this.apacheSourceArchiveDownloader = catalogDependencies.apacheSourceArchiveDownloader();
        this.zipJavaFileScanner = sourceDependencies.zipJavaFileScanner();
        this.javaDeclaredTypeExtractor = sourceDependencies.javaDeclaredTypeExtractor();
        this.productionJavaClassFilter = sourceDependencies.productionJavaClassFilter();
        this.csvWriter = sourceDependencies.csvWriter();
        this.repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl must not be null");
        this.sourceLocator = new JavaSourceLocator();
    }

    public void generate() {
        final List<ReleaseInfo> allReleases = releaseCatalogReader.readReleases();
        final List<ReleaseInfo> selectedReleases = releaseSelectionService.selectOldestThird(allReleases);

        ApplicationLog.info("Total releases found: " + allReleases.size());
        ApplicationLog.info("Selected oldest releases (first 33%): " + selectedReleases.size());

        final List<JavaClassRecord> records = new ArrayList<>();
        final Set<String> seenReleaseAndPath = new LinkedHashSet<>();

        try (TemporaryGitRepository repository = TemporaryGitRepository.cloneRepository(repositoryUrl)) {
            for (ReleaseInfo releaseInfo : selectedReleases) {
                resolveSnapshot(releaseInfo).ifPresent(snapshot -> processRelease(
                        repository,
                        snapshot,
                        records,
                        seenReleaseAndPath
                ));
            }
        }

        csvWriter.write(records);
        ApplicationLog.info("Generated ReleaseClassInventory.csv with rows: " + records.size());
    }

    private Optional<ReleaseCommitSnapshot> resolveSnapshot(final ReleaseInfo releaseInfo) {
        final Optional<ReleaseCommitSnapshot> snapshot = gitHubTagResolver.resolve(releaseInfo);
        if (snapshot.isEmpty()) {
            ApplicationLog.info("Skipping release " + releaseInfo.version() + ": no matching tag found");
        }
        return snapshot;
    }

    private void processRelease(
            final TemporaryGitRepository repository,
            final ReleaseCommitSnapshot snapshot,
            final List<JavaClassRecord> records,
            final Set<String> seenReleaseAndPath
    ) {
        final Path archivePath = apacheSourceArchiveDownloader
                .downloadSourceArchive(snapshot.version())
                .orElseGet(() -> gitHubArchiveDownloader.downloadReleaseArchive(snapshot.tagName()));

        try {
            final List<JavaSourceUnit> javaSources = zipJavaFileScanner.scanJavaFiles(archivePath);
            final ReleaseInventoryStats stats = collectReleaseTypes(repository, snapshot, javaSources, records, seenReleaseAndPath);
            logReleaseStats(snapshot, javaSources.size(), stats);
        } finally {
            deleteQuietly(archivePath);
        }
    }

    private ReleaseInventoryStats collectReleaseTypes(
            final TemporaryGitRepository repository,
            final ReleaseCommitSnapshot snapshot,
            final List<JavaSourceUnit> javaSources,
            final List<JavaClassRecord> records,
            final Set<String> seenReleaseAndPath
    ) {
        final ReleaseInventoryStats stats = new ReleaseInventoryStats();

        for (JavaSourceUnit javaSourceUnit : javaSources) {
            collectSourceTypes(repository, snapshot, javaSourceUnit, records, seenReleaseAndPath, stats);
        }

        return stats;
    }

    private void collectSourceTypes(
            final TemporaryGitRepository repository,
            final ReleaseCommitSnapshot snapshot,
            final JavaSourceUnit javaSourceUnit,
            final List<JavaClassRecord> records,
            final Set<String> seenReleaseAndPath,
            final ReleaseInventoryStats stats
    ) {
        final String normalizedArchivePath = javaSourceUnit.archivePath().replace('\\', '/');
        final List<ExtractedJavaType> extractedTypes = javaDeclaredTypeExtractor.extract(javaSourceUnit);

        for (ExtractedJavaType extractedType : extractedTypes) {
            final InventoryDecision decision = decideInventoryAction(
                    repository,
                    snapshot,
                    normalizedArchivePath,
                    extractedType,
                    seenReleaseAndPath
            );
            applyInventoryDecision(snapshot, normalizedArchivePath, records, stats, decision);
        }
    }

    private InventoryDecision decideInventoryAction(
            final TemporaryGitRepository repository,
            final ReleaseCommitSnapshot snapshot,
            final String normalizedArchivePath,
            final ExtractedJavaType extractedType,
            final Set<String> seenReleaseAndPath
    ) {
        if (!productionJavaClassFilter.isEligible(normalizedArchivePath, extractedType.typeName())) {
            return InventoryDecision.DISCARDED_TYPE;
        }
        if (!isSourceAvailable(repository, snapshot, normalizedArchivePath)) {
            logStaticSuspect(snapshot, normalizedArchivePath);
            return InventoryDecision.STATIC_SUSPECT;
        }
        if (!seenReleaseAndPath.add(snapshot.version() + "|" + normalizedArchivePath)) {
            return InventoryDecision.DUPLICATE;
        }
        return InventoryDecision.ACCEPTED;
    }

    private boolean isSourceAvailable(
            final TemporaryGitRepository repository,
            final ReleaseCommitSnapshot snapshot,
            final String normalizedArchivePath
    ) {
        final SourceLookupResult sourceLookup = sourceLocator.locate(
                repository,
                snapshot.tagName(),
                normalizedArchivePath
        );
        return sourceLookup.found();
    }

    private void applyInventoryDecision(
            final ReleaseCommitSnapshot snapshot,
            final String normalizedArchivePath,
            final List<JavaClassRecord> records,
            final ReleaseInventoryStats stats,
            final InventoryDecision decision
    ) {
        switch (decision) {
            case ACCEPTED -> {
                records.add(new JavaClassRecord(snapshot.version(), normalizedArchivePath, "", "", "NO"));
                stats.acceptedTypes++;
            }
            case DISCARDED_TYPE -> stats.discardedTypes++;
            case DUPLICATE -> stats.droppedDuplicates++;
            case STATIC_SUSPECT -> stats.droppedStaticSuspects++;
        }
    }

    private void logStaticSuspect(
            final ReleaseCommitSnapshot snapshot,
            final String normalizedArchivePath
    ) {
        ApplicationLog.info(
                "[DROP-INVENTORY-STATIC-SUSPECT] release=" + snapshot.version()
                        + " | path=" + normalizedArchivePath
                        + " | reason=source_not_found_at_release_tag"
        );
    }

    private void logReleaseStats(
            final ReleaseCommitSnapshot snapshot,
            final int javaFileCount,
            final ReleaseInventoryStats stats
    ) {
        ApplicationLog.info(
                "Release " + snapshot.version()
                        + " | tag=" + snapshot.tagName()
                        + " | commit=" + snapshot.commitHash()
                        + " | javaFiles=" + javaFileCount
                        + " | acceptedTypes=" + stats.acceptedTypes
                        + " | discardedTypes=" + stats.discardedTypes
                        + " | droppedDuplicates=" + stats.droppedDuplicates
                        + " | droppedStaticSuspects=" + stats.droppedStaticSuspects
        );
    }

    private void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best effort cleanup: a leftover downloaded archive can be safely overwritten on the next run.
        }
    }

    public record ReleaseCatalogDependencies(
            TicketDetailsReleaseCatalogReader releaseCatalogReader,
            ReleaseSelectionService releaseSelectionService,
            GitHubTagResolver gitHubTagResolver,
            GitHubArchiveDownloader gitHubArchiveDownloader,
            ApacheSourceArchiveDownloader apacheSourceArchiveDownloader
    ) {
    }

    public record ReleaseSourceDependencies(
            ZipJavaFileScanner zipJavaFileScanner,
            JavaDeclaredTypeExtractor javaDeclaredTypeExtractor,
            ProductionJavaClassFilter productionJavaClassFilter,
            ReleaseClassInventoryCsvWriter csvWriter
    ) {
    }

    private enum InventoryDecision {
        ACCEPTED,
        DISCARDED_TYPE,
        DUPLICATE,
        STATIC_SUSPECT
    }

    private static final class ReleaseInventoryStats {
        private int acceptedTypes;
        private int discardedTypes;
        private int droppedDuplicates;
        private int droppedStaticSuspects;
    }
}
