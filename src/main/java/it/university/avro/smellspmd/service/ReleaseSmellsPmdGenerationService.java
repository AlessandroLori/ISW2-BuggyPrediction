package it.university.avro.smellspmd.service;

import it.university.avro.common.ApplicationLog;

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
        final List<String> orderedVersions = new ArrayList<>(recordsByVersion.keySet());
        final List<ReleaseMetricsRecord> outputRecords = new ArrayList<>(metricsRecords.size());

        try (TemporaryGitRepository repository = TemporaryGitRepository.cloneRepository(repositoryUrl)) {
            for (int versionIndex = 0; versionIndex < orderedVersions.size(); versionIndex++) {
                final String currentVersion = orderedVersions.get(versionIndex);
                final List<ReleaseMetricsRecord> currentVersionRecords = recordsByVersion.get(currentVersion);

                if (versionIndex == 0) {
                    ApplicationLog.info(
                            "Analyzing PMD smells for release " + currentVersion
                                    + " | previousRelease=NONE | strategy=zero_by_construction"
                    );
                    addZeroSmellRecords(currentVersionRecords, outputRecords);
                    continue;
                }

                final String previousVersion = orderedVersions.get(versionIndex - 1);
                final String previousTag = repository.resolveTag(previousVersion)
                        .orElseThrow(() -> new IllegalStateException(
                                "Unable to resolve git tag for previous version " + previousVersion
                        ));

                ApplicationLog.info(
                        "Analyzing PMD smells for release " + currentVersion
                                + " using previous release " + previousVersion
                                + " with tag " + previousTag
                );

                final ReleaseSourceSnapshot previousReleaseSnapshot = releaseSourceSnapshotBuilder.build(
                        repository,
                        previousTag,
                        currentVersionRecords
                );

                final Map<String, PmdClassSmellMetrics> smellMetricsByResolvedClassPath = smellAnalyzer.analyzeByClassPath(
                        previousReleaseSnapshot.sourceByResolvedClassPath(),
                        pmdRulesetPath
                );

                for (ReleaseMetricsRecord metricsRecord : currentVersionRecords) {
                    final ResolvedSourceFile sourceFile = previousReleaseSnapshot.sourceFor(metricsRecord.classPath());
                    final PmdClassSmellMetrics smellMetrics = resolveSmellMetrics(sourceFile, smellMetricsByResolvedClassPath);
                    outputRecords.add(withUpdatedSmells(metricsRecord, smellMetrics));
                }
            }
        }

        metricsWriter.write(outputRecords);
        ApplicationLog.info("Generated PMD-enriched metrics csv: " + outputMetricsCsvPath + " | rows=" + outputRecords.size());
    }

    private Map<String, List<ReleaseMetricsRecord>> groupByVersion(final List<ReleaseMetricsRecord> metricsRecords) {
        final Map<String, List<ReleaseMetricsRecord>> grouped = new LinkedHashMap<>();
        for (ReleaseMetricsRecord metricsRecord : metricsRecords) {
            grouped.computeIfAbsent(metricsRecord.version(), ignored -> new ArrayList<>()).add(metricsRecord);
        }
        return grouped;
    }

    private void addZeroSmellRecords(
            final List<ReleaseMetricsRecord> versionRecords,
            final List<ReleaseMetricsRecord> outputRecords
    ) {
        for (ReleaseMetricsRecord metricsRecord : versionRecords) {
            outputRecords.add(withUpdatedSmells(metricsRecord, PmdClassSmellMetrics.empty()));
        }
    }

    private ReleaseMetricsRecord withUpdatedSmells(
            final ReleaseMetricsRecord metricsRecord,
            final PmdClassSmellMetrics smellMetrics
    ) {
        return new ReleaseMetricsRecord(
                metricsRecord.version(),
                metricsRecord.classPath(),
                metricsRecord.loc(),
                metricsRecord.locTouched(),
                metricsRecord.revs(),
                metricsRecord.fixes(),
                metricsRecord.auth(),
                metricsRecord.locAdded(),
                metricsRecord.maxLocAdded(),
                metricsRecord.avgLocAdded(),
                metricsRecord.churn(),
                metricsRecord.maxChurn(),
                metricsRecord.avgChurn(),
                metricsRecord.changeSetSize(),
                metricsRecord.maxChangeSet(),
                metricsRecord.avgChangeSet(),
                metricsRecord.age(),
                metricsRecord.weightedAge(),
                metricsRecord.commentLines(),
                Integer.toString(smellMetrics.smellCount()),
                smellMetrics.distinctSmellTypes(),
                metricsRecord.nestingDepth(),
                metricsRecord.decisionPoints(),
                metricsRecord.buggy()
        );
    }

    private PmdClassSmellMetrics resolveSmellMetrics(
            final ResolvedSourceFile sourceFile,
            final Map<String, PmdClassSmellMetrics> smellMetricsByResolvedClassPath
    ) {
        if (!sourceFile.found()) {
            ApplicationLog.info(
                    "[PMD-SKIP] requested=" + sourceFile.requestedClassPath()
                            + " | reason=source_not_found_in_previous_release"
            );
            return PmdClassSmellMetrics.empty();
        }

        final PmdClassSmellMetrics smellMetrics = smellMetricsByResolvedClassPath.getOrDefault(
                normalizePath(sourceFile.resolvedClassPath()),
                PmdClassSmellMetrics.empty()
        );

        if (!sourceFile.exactMatch()) {
            ApplicationLog.info(
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
