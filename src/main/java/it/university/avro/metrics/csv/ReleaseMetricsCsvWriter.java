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

                for (ReleaseMetricsRecord metricsRecord : records) {
                    writer.write(csv(metricsRecord.version()));
                    writer.write(",");
                    writer.write(csv(metricsRecord.classPath()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.loc()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.locTouched()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.revs()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.fixes()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.auth()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.locAdded()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.maxLocAdded()));
                    writer.write(",");
                    writer.write(metricsRecord.avgLocAddedAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.churn()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.maxChurn()));
                    writer.write(",");
                    writer.write(metricsRecord.avgChurnAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.changeSetSize()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.maxChangeSet()));
                    writer.write(",");
                    writer.write(metricsRecord.avgChangeSetAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.age()));
                    writer.write(",");
                    writer.write(metricsRecord.weightedAgeAsCsv());
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.commentLines()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.nestingDepth()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.decisionPoints()));
                    writer.write(",");
                    writer.write(csv(metricsRecord.nsmells()));
                    writer.write(",");
                    writer.write(Integer.toString(metricsRecord.distinctSmellTypes()));
                    writer.write(",");
                    writer.write(csv(metricsRecord.buggy()));
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
