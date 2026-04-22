package it.university.avro.smellspmd.service;

import it.university.avro.metrics.csv.ReleaseMetricsCsvWriter;
import it.university.avro.metrics.domain.ReleaseMetricsRecord;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.smellspmd.csv.ReleaseMetricsCsvReader;
import it.university.avro.smellspmd.domain.PmdClassSmellMetrics;
import it.university.avro.smellspmd.domain.ReleaseSourceSnapshot;
import it.university.avro.smellspmd.domain.ResolvedSourceFile;
import it.university.avro.smellspmd.pmd.PmdJavaSmellAnalyzer;
import it.university.avro.smellspmd.source.ReleaseSourceSnapshotBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReleaseSmellsPmdGenerationService {

    private final ReleaseMetricsCsvReader metricsReader;
    private final ReleaseMetricsCsvWriter metricsWriter;
    private final ReleaseSourceSnapshotBuilder releaseSourceSnapshotBuilder;
    private final PmdJavaSmellAnalyzer smellAnalyzer;

    public ReleaseSmellsPmdGenerationService(
            final ReleaseMetricsCsvReader metricsReader,
            final ReleaseMetricsCsvWriter metricsWriter,
            final ReleaseSourceSnapshotBuilder releaseSourceSnapshotBuilder,
            final PmdJavaSmellAnalyzer smellAnalyzer
    ) {
        this.metricsReader = metricsReader;
        this.metricsWriter = metricsWriter;
        this.releaseSourceSnapshotBuilder = releaseSourceSnapshotBuilder;
        this.smellAnalyzer = smellAnalyzer;
    }

    public void generate(
            final Path inputMetricsCsvPath,
            final Path outputMetricsCsvPath,
            final String repositoryUrl,
            final String pmdRulesetPath
    ) {
        final List<ReleaseMetricsRecord> metricsRecords = metricsReader.read(inputMetricsCsvPath);
        final Map<String, List<ReleaseMetricsRecord>> recordsByVersion = groupByVersion(metricsRecords);
        final List<ReleaseMetricsRecord> outputRecords = new ArrayList<>(metricsRecords.size());

        try (TemporaryGitRepository repository = TemporaryGitRepository.cloneRepository(repositoryUrl)) {
            for (Map.Entry<String, List<ReleaseMetricsRecord>> versionEntry : recordsByVersion.entrySet()) {
                final String version = versionEntry.getKey();
                final String tag = repository.resolveTag(version)
                        .orElseThrow(() -> new IllegalStateException(
                                "Unable to resolve git tag for version " + version
                        ));

                System.out.println("Analyzing PMD smells for release " + version + " with tag " + tag);

                final ReleaseSourceSnapshot releaseSnapshot = releaseSourceSnapshotBuilder.build(
                        repository,
                        tag,
                        versionEntry.getValue()
                );

                final Map<String, PmdClassSmellMetrics> smellMetricsByResolvedClassPath = smellAnalyzer.analyzeByClassPath(
                        releaseSnapshot.sourceByResolvedClassPath(),
                        pmdRulesetPath
                );

                for (ReleaseMetricsRecord record : versionEntry.getValue()) {
                    final ResolvedSourceFile sourceFile = releaseSnapshot.sourceFor(record.classPath());
                    final PmdClassSmellMetrics smellMetrics = resolveSmellMetrics(sourceFile, smellMetricsByResolvedClassPath);

                    outputRecords.add(new ReleaseMetricsRecord(
                            record.version(),
                            record.classPath(),
                            record.loc(),
                            record.locTouched(),
                            record.revs(),
                            record.fixes(),
                            record.auth(),
                            record.locAdded(),
                            record.maxLocAdded(),
                            record.avgLocAdded(),
                            record.churn(),
                            record.maxChurn(),
                            record.avgChurn(),
                            record.changeSetSize(),
                            record.maxChangeSet(),
                            record.avgChangeSet(),
                            record.age(),
                            record.weightedAge(),
                            record.commentLines(),
                            Integer.toString(smellMetrics.smellCount()),
                            smellMetrics.distinctSmellTypes(),
                            record.nestingDepth(),
                            record.decisionPoints(),
                            record.buggy()
                    ));
                }
            }
        }

        metricsWriter.write(outputRecords);
        System.out.println("Generated PMD-enriched metrics csv: " + outputMetricsCsvPath + " | rows=" + outputRecords.size());
    }

    private Map<String, List<ReleaseMetricsRecord>> groupByVersion(final List<ReleaseMetricsRecord> metricsRecords) {
        final Map<String, List<ReleaseMetricsRecord>> grouped = new LinkedHashMap<>();
        for (ReleaseMetricsRecord record : metricsRecords) {
            grouped.computeIfAbsent(record.version(), ignored -> new ArrayList<>()).add(record);
        }
        return grouped;
    }

    private PmdClassSmellMetrics resolveSmellMetrics(
            final ResolvedSourceFile sourceFile,
            final Map<String, PmdClassSmellMetrics> smellMetricsByResolvedClassPath
    ) {
        if (!sourceFile.found()) {
            System.out.println(
                    "[PMD-SKIP] requested=" + sourceFile.requestedClassPath()
                            + " | reason=source_not_found_at_release_tag"
            );
            return PmdClassSmellMetrics.empty();
        }

        final PmdClassSmellMetrics smellMetrics = smellMetricsByResolvedClassPath.getOrDefault(
                normalizePath(sourceFile.resolvedClassPath()),
                PmdClassSmellMetrics.empty()
        );

        if (!sourceFile.exactMatch()) {
            System.out.println(
                    "[PMD-PATH-RECOVERED] requested=" + sourceFile.requestedClassPath()
                            + " | resolved=" + sourceFile.resolvedClassPath()
            );
        }

        return smellMetrics;
    }

    private String normalizePath(final String path) {
        return path.replace('\\', '/');
    }
}
