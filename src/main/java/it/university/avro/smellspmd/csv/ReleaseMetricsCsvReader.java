package it.university.avro.smellspmd.csv;

import it.university.avro.metrics.csv.SimpleCsvParser;
import it.university.avro.metrics.domain.ReleaseMetricsRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReleaseMetricsCsvReader {

    private final SimpleCsvParser csvParser = new SimpleCsvParser();

    public List<ReleaseMetricsRecord> read(final Path csvPath) {
        try {
            final List<String> lines = Files.readAllLines(csvPath);
            if (lines.isEmpty()) {
                return List.of();
            }

            final List<String> header = csvParser.parseLine(lines.get(0));
            final ColumnIndexes indexes = resolveColumnIndexes(header);
            final List<ReleaseMetricsRecord> records = new ArrayList<>();
            for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
                parseMetricsLine(lines.get(lineIndex), indexes).ifPresent(records::add);
            }

            return List.copyOf(records);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read metrics csv " + csvPath, exception);
        }
    }

    private Optional<ReleaseMetricsRecord> parseMetricsLine(
            final String rawLine,
            final ColumnIndexes indexes
    ) {
        if (rawLine.isBlank()) {
            return Optional.empty();
        }

        final List<String> values = csvParser.parseLine(rawLine);
        if (values.size() <= indexes.buggyIndex()) {
            return Optional.empty();
        }

        return Optional.of(new ReleaseMetricsRecord(
                values.get(indexes.versionIndex()).trim(),
                values.get(indexes.classPathIndex()).trim(),
                parseInteger(values.get(indexes.locIndex())),
                parseInteger(values.get(indexes.locTouchedIndex())),
                parseInteger(values.get(indexes.revsIndex())),
                parseInteger(values.get(indexes.fixesIndex())),
                parseInteger(values.get(indexes.authIndex())),
                parseInteger(values.get(indexes.locAddedIndex())),
                parseInteger(values.get(indexes.maxLocAddedIndex())),
                parseDouble(values.get(indexes.avgLocAddedIndex())),
                parseInteger(values.get(indexes.churnIndex())),
                parseInteger(values.get(indexes.maxChurnIndex())),
                parseDouble(values.get(indexes.avgChurnIndex())),
                parseInteger(values.get(indexes.changeSetSizeIndex())),
                parseInteger(values.get(indexes.maxChangeSetIndex())),
                parseDouble(values.get(indexes.avgChangeSetIndex())),
                parseInteger(values.get(indexes.ageIndex())),
                parseDouble(values.get(indexes.weightedAgeIndex())),
                parseInteger(values.get(indexes.commentLinesIndex())),
                values.get(indexes.nsmellsIndex()).trim(),
                parseInteger(values.get(indexes.distinctSmellTypesIndex())),
                parseInteger(values.get(indexes.nestingDepthIndex())),
                parseInteger(values.get(indexes.decisionPointsIndex())),
                values.get(indexes.buggyIndex()).trim()
        ));
    }

    private ColumnIndexes resolveColumnIndexes(final List<String> header) {
        return new ColumnIndexes(
                findRequiredIndex(header, "version"),
                findRequiredIndex(header, "classpath"),
                findRequiredIndex(header, "LOC"),
                findRequiredIndex(header, "LOC_TOUCHED"),
                findRequiredIndex(header, "REVS"),
                findRequiredIndex(header, "FIXES"),
                findRequiredIndex(header, "AUTH"),
                findRequiredIndex(header, "LOC_ADDED"),
                findRequiredIndex(header, "MAX_LOC_ADDED"),
                findRequiredIndex(header, "AVG_LOC_ADDED"),
                findRequiredIndex(header, "CHURN"),
                findRequiredIndex(header, "MAX_CHURN"),
                findRequiredIndex(header, "AVG_CHURN"),
                findRequiredIndex(header, "CHANGE_SET_SIZE"),
                findRequiredIndex(header, "MAX_CHANGE_SET"),
                findRequiredIndex(header, "AVG_CHANGE_SET"),
                findRequiredIndex(header, "AGE"),
                findRequiredIndex(header, "WEIGHTED_AGE"),
                findRequiredIndex(header, "COMMENT_LINES"),
                findRequiredIndex(header, "nsmells"),
                findRequiredIndex(header, "DISTINCT_SMELL_TYPES"),
                findRequiredIndex(header, "NESTING_DEPTH"),
                findRequiredIndex(header, "DECISION_POINTS"),
                findRequiredIndex(header, "BUGGY")
        );
    }

    private int findRequiredIndex(final List<String> header, final String columnName) {
        final int index = header.indexOf(columnName);
        if (index < 0) {
            throw new IllegalStateException("Missing required column in ReleaseMetrics.csv: " + columnName);
        }
        return index;
    }

    private int parseInteger(final String rawValue) {
        final String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isBlank()) {
            return 0;
        }
        return Integer.parseInt(normalized);
    }

    private double parseDouble(final String rawValue) {
        final String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(normalized);
    }

    private record ColumnIndexes(
            int versionIndex,
            int classPathIndex,
            int locIndex,
            int locTouchedIndex,
            int revsIndex,
            int fixesIndex,
            int authIndex,
            int locAddedIndex,
            int maxLocAddedIndex,
            int avgLocAddedIndex,
            int churnIndex,
            int maxChurnIndex,
            int avgChurnIndex,
            int changeSetSizeIndex,
            int maxChangeSetIndex,
            int avgChangeSetIndex,
            int ageIndex,
            int weightedAgeIndex,
            int commentLinesIndex,
            int nsmellsIndex,
            int distinctSmellTypesIndex,
            int nestingDepthIndex,
            int decisionPointsIndex,
            int buggyIndex
    ) {
    }
}
