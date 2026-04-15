package it.university.avro.releasesnapshot;

import it.university.avro.releasesnapshot.config.ReleaseSnapshotConfiguration;
import it.university.avro.releasesnapshot.csv.ReleaseClassInventoryCsvWriter;
import it.university.avro.releasesnapshot.csv.TicketDetailsReleaseCatalogReader;
import it.university.avro.releasesnapshot.github.GitHubApiClient;
import it.university.avro.releasesnapshot.github.GitHubArchiveDownloader;
import it.university.avro.releasesnapshot.github.GitHubTagResolver;
import it.university.avro.releasesnapshot.scan.JavaDeclaredTypeExtractor;
import it.university.avro.releasesnapshot.scan.LogicalClassPathResolver;
import it.university.avro.releasesnapshot.scan.ProductionJavaClassFilter;
import it.university.avro.releasesnapshot.scan.ZipJavaFileScanner;
import it.university.avro.releasesnapshot.service.ReleaseClassInventoryService;
import it.university.avro.releasesnapshot.service.ReleaseSelectionService;
import it.university.avro.releasesnapshot.archive.ApacheSourceArchiveDownloader;

public final class  ReleaseSnapshotApplication {

    private ReleaseSnapshotApplication() {
    }

    public static void main(final String[] args) {
        final ReleaseSnapshotConfiguration configuration = ReleaseSnapshotConfiguration.defaultConfiguration();

        final TicketDetailsReleaseCatalogReader releaseCatalogReader =
                new TicketDetailsReleaseCatalogReader(configuration.ticketDetailsCsvPath());

        final ReleaseSelectionService releaseSelectionService =
                new ReleaseSelectionService();

        final GitHubApiClient gitHubApiClient =
                new GitHubApiClient(
                        configuration.owner(),
                        configuration.repository(),
                        configuration.gitHubToken(),
                        configuration.gitHubApiVersion()
                );

        final GitHubTagResolver gitHubTagResolver =
                new GitHubTagResolver(gitHubApiClient);

        final GitHubArchiveDownloader gitHubArchiveDownloader =
                new GitHubArchiveDownloader(gitHubApiClient);

        final ZipJavaFileScanner zipJavaFileScanner =
                new ZipJavaFileScanner();

        final LogicalClassPathResolver logicalClassPathResolver =
                new LogicalClassPathResolver();

        final JavaDeclaredTypeExtractor javaDeclaredTypeExtractor =
                new JavaDeclaredTypeExtractor(logicalClassPathResolver);

        final ProductionJavaClassFilter productionJavaClassFilter =
                new ProductionJavaClassFilter();

        final ReleaseClassInventoryCsvWriter csvWriter =
                new ReleaseClassInventoryCsvWriter(configuration.outputCsvPath());

        final ApacheSourceArchiveDownloader apacheSourceArchiveDownloader =
                new ApacheSourceArchiveDownloader();

        final ReleaseClassInventoryService service = new ReleaseClassInventoryService(
                releaseCatalogReader,
                releaseSelectionService,
                gitHubTagResolver,
                gitHubArchiveDownloader,
                apacheSourceArchiveDownloader,
                zipJavaFileScanner,
                javaDeclaredTypeExtractor,
                productionJavaClassFilter,
                csvWriter
        );

        service.generate();
    }
}