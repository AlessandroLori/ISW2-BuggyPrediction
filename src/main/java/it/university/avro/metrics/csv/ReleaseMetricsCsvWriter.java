package it.university.avro.metrics.csv;

import it.university.avro.metrics.domain.ReleaseMetricsRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ReleaseMetricsCsvWriter {

    private final Path outputCsvPath;

    public ReleaseMetricsCsvWriter(final Path outputCsvPath) {
        this.outputCsvPath = outputCsvPath;
    }

    public void write(final List<ReleaseMetricsRecord> records) {
        try {
            final Path parentDirectory = outputCsvPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputCsvPath)) {
                writer.write("version,classpath,LOC,LOC_TOUCHED,REVS,FIXES,AUTH,LOC_ADDED,MAX_LOC_ADDED,AVG_LOC_ADDED,CHURN,MAX_CHURN,AVG_CHURN,CHANGE_SET_SIZE,MAX_CHANGE_SET,AVG_CHANGE_SET,AGE,WEIGHTED_AGE,COMMENT_LINES,NESTING_DEPTH,DECISION_POINTS,nsmells,DISTINCT_SMELL_TYPES,BUGGY");
                writer.newLine();

                for (ReleaseMetricsRecord record : records) {
                    writer.write(csv(record.version()));
                    writer.write(",");
                    writer.write(csv(record.classPath()));
                    writer.write(",");
                    writer.write(Integer.toString(record.loc()));
                    writer.write(",");
                    writer.write(Integer.toString(record.locTouched()));
                    writer.write(",");
                    writer.write(Integer.toString(record.revs()));
                    writer.write(",");
                    writer.write(Integer.toString(record.fixes()));
                    writer.write(",");
                    writer.write(Integer.toString(record.auth()));
                    writer.write(",");
                    writer.write(Integer.toString(record.locAdded()));
                    writer.write(",");
                    writer.write(Integer.toString(record.maxLocAdded()));
                    writer.write(",");
                    writer.write(record.avgLocAddedAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(record.churn()));
                    writer.write(",");
                    writer.write(Integer.toString(record.maxChurn()));
                    writer.write(",");
                    writer.write(record.avgChurnAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(record.changeSetSize()));
                    writer.write(",");
                    writer.write(Integer.toString(record.maxChangeSet()));
                    writer.write(",");
                    writer.write(record.avgChangeSetAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(record.age()));
                    writer.write(",");
                    writer.write(record.weightedAgeAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(record.commentLines()));
                    writer.write(",");
                    writer.write(Integer.toString(record.nestingDepth()));
                    writer.write(",");
                    writer.write(Integer.toString(record.decisionPoints()));
                    writer.write(",");
                    writer.write(csv(record.nsmells()));
                    writer.write(",");
                    writer.write(Integer.toString(record.distinctSmellTypes()));
                    writer.write(",");
                    writer.write(csv(record.buggy()));
                    writer.newLine();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write metrics csv to " + outputCsvPath, exception);
        }
    }

    private String csv(final String value) {
        final String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
