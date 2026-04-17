package it.university.avro.releasesnapshot.service;

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
            final TicketDetailsReleaseCatalogReader releaseCatalogReader,
            final ReleaseSelectionService releaseSelectionService,
            final GitHubTagResolver gitHubTagResolver,
            final GitHubArchiveDownloader gitHubArchiveDownloader,
            final ApacheSourceArchiveDownloader apacheSourceArchiveDownloader,
            final ZipJavaFileScanner zipJavaFileScanner,
            final JavaDeclaredTypeExtractor javaDeclaredTypeExtractor,
            final ProductionJavaClassFilter productionJavaClassFilter,
            final ReleaseClassInventoryCsvWriter csvWriter,
            final String repositoryUrl
    ) {
        this.releaseCatalogReader = releaseCatalogReader;
        this.releaseSelectionService = releaseSelectionService;
        this.gitHubTagResolver = gitHubTagResolver;
        this.gitHubArchiveDownloader = gitHubArchiveDownloader;
        this.apacheSourceArchiveDownloader = apacheSourceArchiveDownloader;
        this.zipJavaFileScanner = zipJavaFileScanner;
        this.javaDeclaredTypeExtractor = javaDeclaredTypeExtractor;
        this.productionJavaClassFilter = productionJavaClassFilter;
        this.csvWriter = csvWriter;
        this.repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl must not be null");
        this.sourceLocator = new JavaSourceLocator();
    }

    public void generate() {
        final List<ReleaseInfo> allReleases = releaseCatalogReader.readReleases();
        final List<ReleaseInfo> selectedReleases = releaseSelectionService.selectOldestThird(allReleases);

        System.out.println("Total releases found: " + allReleases.size());
        System.out.println("Selected oldest releases (first 33%): " + selectedReleases.size());

        final List<JavaClassRecord> records = new ArrayList<>();
        final Set<String> seenReleaseAndPath = new LinkedHashSet<>();

        try (TemporaryGitRepository repository = TemporaryGitRepository.cloneRepository(repositoryUrl)) {
            for (ReleaseInfo releaseInfo : selectedReleases) {
                final ReleaseCommitSnapshot snapshot = gitHubTagResolver.resolve(releaseInfo)
                        .orElseGet(() -> {
                            System.out.println("Skipping release " + releaseInfo.version() + ": no matching tag found");
                            return null;
                        });

                if (snapshot == null) {
                    continue;
                }

                final Path archivePath = apacheSourceArchiveDownloader
                        .downloadSourceArchive(releaseInfo.version())
                        .orElseGet(() -> gitHubArchiveDownloader.downloadReleaseArchive(snapshot.tagName()));

                try {
                    final List<JavaSourceUnit> javaSources = zipJavaFileScanner.scanJavaFiles(archivePath);

                    int acceptedTypes = 0;
                    int discardedTypes = 0;
                    int droppedDuplicates = 0;
                    int droppedStaticSuspects = 0;

                    for (JavaSourceUnit javaSourceUnit : javaSources) {
                        final List<ExtractedJavaType> extractedTypes =
                                javaDeclaredTypeExtractor.extract(javaSourceUnit);

                        for (ExtractedJavaType extractedType : extractedTypes) {
                            final String normalizedArchivePath = javaSourceUnit.archivePath().replace('\\', '/');

                            if (!productionJavaClassFilter.isEligible(
                                    normalizedArchivePath,
                                    extractedType.typeName()
                            )) {
                                discardedTypes++;
                                continue;
                            }

                            final SourceLookupResult sourceLookup = sourceLocator.locate(
                                    repository,
                                    snapshot.tagName(),
                                    normalizedArchivePath
                            );

                            if (!sourceLookup.found()) {
                                droppedStaticSuspects++;
                                System.out.println(
                                        "[DROP-INVENTORY-STATIC-SUSPECT] release=" + snapshot.version()
                                                + " | path=" + normalizedArchivePath
                                                + " | reason=source_not_found_at_release_tag"
                                );
                                continue;
                            }

                            final String dedupKey = snapshot.version() + "|" + normalizedArchivePath;
                            if (!seenReleaseAndPath.add(dedupKey)) {
                                droppedDuplicates++;
                                continue;
                            }

                            records.add(new JavaClassRecord(
                                    snapshot.version(),
                                    normalizedArchivePath,
                                    "",
                                    "",
                                    "NO"
                            ));
                            acceptedTypes++;
                        }
                    }

                    System.out.println(
                            "Release " + snapshot.version()
                                    + " | tag=" + snapshot.tagName()
                                    + " | commit=" + snapshot.commitHash()
                                    + " | javaFiles=" + javaSources.size()
                                    + " | acceptedTypes=" + acceptedTypes
                                    + " | discardedTypes=" + discardedTypes
                                    + " | droppedDuplicates=" + droppedDuplicates
                                    + " | droppedStaticSuspects=" + droppedStaticSuspects
                    );
                } finally {
                    deleteQuietly(archivePath);
                }
            }
        }

        csvWriter.write(records);
        System.out.println("Generated ReleaseClassInventory.csv with rows: " + records.size());
    }

    private void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}