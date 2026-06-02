package it.university.avro.smelldataset.domain;

import java.util.Locale;
import java.util.Objects;

public record SmellDatasetRecord(
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
        int nestingDepth,
        int decisionPoints,
        int distinctSmellTypes,
        int nsmells
) {

    public SmellDatasetRecord {
        classPath = Objects.requireNonNull(classPath, "classPath must not be null").replace('\\', '/');
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
