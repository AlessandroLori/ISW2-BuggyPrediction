package it.university.avro.metrics.domain;

import java.util.Locale;
import java.util.Objects;

public record ReleaseMetricsRecord(
        String version,
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
        String nsmells,
        int distinctSmellTypes,
        int nestingDepth,
        int decisionPoints,
        String buggy
) {
    public ReleaseMetricsRecord {
        version = Objects.requireNonNull(version, "version must not be null");
        classPath = Objects.requireNonNull(classPath, "classPath must not be null");
        nsmells = Objects.requireNonNull(nsmells, "nsmells must not be null");
        buggy = Objects.requireNonNull(buggy, "buggy must not be null");
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
