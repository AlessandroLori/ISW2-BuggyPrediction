package it.university.avro.smelldataset.csv;

import it.university.avro.smelldataset.domain.SmellDatasetRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SmellDatasetCsvWriter {

    private static final String[] HEADERS = {
            "classpath",
            "LOC",
            "LOC_TOUCHED",
            "REVS",
            "FIXES",
            "AUTH",
            "LOC_ADDED",
            "MAX_LOC_ADDED",
            "AVG_LOC_ADDED",
            "CHURN",
            "MAX_CHURN",
            "AVG_CHURN",
            "CHANGE_SET_SIZE",
            "MAX_CHANGE_SET",
            "AVG_CHANGE_SET",
            "AGE",
            "WEIGHTED_AGE",
            "COMMENT_LINES",
            "NESTING_DEPTH",
            "DECISION_POINTS",
            "DISTINCT_SMELL_TYPES",
            "nsmells"
    };

    public void write(final Path outputPath, final List<SmellDatasetRecord> records) {
        try {
            final Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(
                         writer,
                         CSVFormat.DEFAULT.builder()
                                 .setHeader(HEADERS)
                                 .build()
                 )) {
                for (SmellDatasetRecord smellRecord : records) {
                    printRecord(printer, smellRecord);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write smell dataset csv to " + outputPath, exception);
        }
    }

    private void printRecord(
            final CSVPrinter printer,
            final SmellDatasetRecord smellRecord
    ) throws IOException {
        printer.printRecord(
                smellRecord.classPath(),
                smellRecord.loc(),
                smellRecord.locTouched(),
                smellRecord.revs(),
                smellRecord.fixes(),
                smellRecord.auth(),
                smellRecord.locAdded(),
                smellRecord.maxLocAdded(),
                smellRecord.avgLocAddedAsCsv(),
                smellRecord.churn(),
                smellRecord.maxChurn(),
                smellRecord.avgChurnAsCsv(),
                smellRecord.changeSetSize(),
                smellRecord.maxChangeSet(),
                smellRecord.avgChangeSetAsCsv(),
                smellRecord.age(),
                smellRecord.weightedAgeAsCsv(),
                smellRecord.commentLines(),
                smellRecord.nestingDepth(),
                smellRecord.decisionPoints(),
                smellRecord.distinctSmellTypes(),
                smellRecord.nsmells()
        );
    }
}
