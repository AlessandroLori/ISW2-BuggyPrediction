package it.university.avro.refactoringdataset.domain;

import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.domain.StaticMetrics;

import java.util.Locale;
import java.util.Objects;

public record RefactoringMetricsRecord(
        String classPath,
        int loc,
        int locTouched,
        int revs,
        int fixes,
        int auth,
        int locAdded,
        int maxLocAdded,
        double avgLocAdded,
        int churn,
        int maxChurn,
        double avgChurn,
        int changeSetSize,
        int maxChangeSet,
        double avgChangeSet,
        int age,
        double weightedAge,
        int commentLines,
        int distinctSmellTypes,
        int nestingDepth,
        int decisionPoints,
        int pmdSmells
) {
    public RefactoringMetricsRecord {
        classPath = Objects.requireNonNull(classPath, "classPath must not be null");
    }

    public static RefactoringMetricsRecord from(
            String classPath,
            StaticMetrics staticMetrics,
            HistoryMetrics historyMetrics,
            int distinctSmellTypes,
            int pmdSmells
    ) {
        return new RefactoringMetricsRecord(
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
                distinctSmellTypes,
                staticMetrics.nestingDepth(),
                staticMetrics.decisionPoints(),
                pmdSmells
        );
    }

    public String avgLocAddedAsCsv() {
        return String.format(Locale.ROOT, "%.6f", avgLocAdded);
    }

    public String avgChurnAsCsv() {
        return String.format(Locale.ROOT, "%.6f", avgChurn);
    }

    public String avgChangeSetAsCsv() {
        return String.format(Locale.ROOT, "%.6f", avgChangeSet);
    }

    public String weightedAgeAsCsv() {
        return String.format(Locale.ROOT, "%.6f", weightedAge);
    }
}
