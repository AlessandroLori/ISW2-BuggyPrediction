package it.university.avro.smellspmd.csv;

import it.university.avro.metrics.csv.SimpleCsvParser;
import it.university.avro.metrics.domain.ReleaseMetricsRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ReleaseMetricsCsvReader {

    private final SimpleCsvParser csvParser = new SimpleCsvParser();

    public List<ReleaseMetricsRecord> read(final Path csvPath) {
        try {
            final List<String> lines = Files.readAllLines(csvPath);
            if (lines.isEmpty()) {
                return List.of();
            }

            final List<String> header = csvParser.parseLine(lines.get(0));

            final int versionIndex = findRequiredIndex(header, "version");
            final int classPathIndex = findRequiredIndex(header, "classpath");
            final int locIndex = findRequiredIndex(header, "LOC");
            final int locTouchedIndex = findRequiredIndex(header, "LOC_TOUCHED");
            final int revsIndex = findRequiredIndex(header, "REVS");
            final int fixesIndex = findRequiredIndex(header, "FIXES");
            final int authIndex = findRequiredIndex(header, "AUTH");
            final int locAddedIndex = findRequiredIndex(header, "LOC_ADDED");
            final int maxLocAddedIndex = findRequiredIndex(header, "MAX_LOC_ADDED");
            final int avgLocAddedIndex = findRequiredIndex(header, "AVG_LOC_ADDED");
            final int churnIndex = findRequiredIndex(header, "CHURN");
            final int maxChurnIndex = findRequiredIndex(header, "MAX_CHURN");
            final int avgChurnIndex = findRequiredIndex(header, "AVG_CHURN");
            final int changeSetSizeIndex = findRequiredIndex(header, "CHANGE_SET_SIZE");
            final int maxChangeSetIndex = findRequiredIndex(header, "MAX_CHANGE_SET");
            final int avgChangeSetIndex = findRequiredIndex(header, "AVG_CHANGE_SET");
            final int ageIndex = findRequiredIndex(header, "AGE");
            final int weightedAgeIndex = findRequiredIndex(header, "WEIGHTED_AGE");
            final int commentLinesIndex = findRequiredIndex(header, "COMMENT_LINES");
            final int nsmellsIndex = findRequiredIndex(header, "nsmells");
            final int distinctSmellTypesIndex = findRequiredIndex(header, "DISTINCT_SMELL_TYPES");
            final int nestingDepthIndex = findRequiredIndex(header, "NESTING_DEPTH");
            final int decisionPointsIndex = findRequiredIndex(header, "DECISION_POINTS");
            final int buggyIndex = findRequiredIndex(header, "BUGGY");

            final List<ReleaseMetricsRecord> records = new ArrayList<>();
            for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
                final String rawLine = lines.get(lineIndex);
                if (rawLine.isBlank()) {
                    continue;
                }

                final List<String> values = csvParser.parseLine(rawLine);
                if (values.size() <= buggyIndex) {
                    continue;
                }

                records.add(new ReleaseMetricsRecord(
                        values.get(versionIndex).trim(),
                        values.get(classPathIndex).trim(),
                        parseInteger(values.get(locIndex)),
                        parseInteger(values.get(locTouchedIndex)),
                        parseInteger(values.get(revsIndex)),
                        parseInteger(values.get(fixesIndex)),
                        parseInteger(values.get(authIndex)),
                        parseInteger(values.get(locAddedIndex)),
                        parseInteger(values.get(maxLocAddedIndex)),
                        parseDouble(values.get(avgLocAddedIndex)),
                        parseInteger(values.get(churnIndex)),
                        parseInteger(values.get(maxChurnIndex)),
                        parseDouble(values.get(avgChurnIndex)),
                        parseInteger(values.get(changeSetSizeIndex)),
                        parseInteger(values.get(maxChangeSetIndex)),
                        parseDouble(values.get(avgChangeSetIndex)),
                        parseInteger(values.get(ageIndex)),
                        parseDouble(values.get(weightedAgeIndex)),
                        parseInteger(values.get(commentLinesIndex)),
                        values.get(nsmellsIndex).trim(),
                        parseInteger(values.get(distinctSmellTypesIndex)),
                        parseInteger(values.get(nestingDepthIndex)),
                        parseInteger(values.get(decisionPointsIndex)),
                        values.get(buggyIndex).trim()
                ));
            }

            return List.copyOf(records);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read metrics csv " + csvPath, exception);
        }
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
}
