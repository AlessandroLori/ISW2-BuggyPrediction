package it.university.avro.refactoringdataset.csv;

import it.university.avro.refactoringdataset.domain.RefactoringMetricsRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RefactoringMetricsCsvWriter {

    private static final String CLASSPATH_COLUMN = "classpath";

    private static final String[] HEADER = {
            CLASSPATH_COLUMN,
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
            "DISTINCT_SMELL_TYPES",
            "NESTING_DEPTH",
            "DECISION_POINTS",
            "SONAR_SMELLS",
            "PMD_SMELLS"
    };

    private final Path outputCsvPath;

    public RefactoringMetricsCsvWriter(Path outputCsvPath) {
        this.outputCsvPath = outputCsvPath;
    }

    public void write(List<RefactoringMetricsRecord> records) {
        Map<String, Map<String, String>> existingRowsByClassPath = readExistingRowsByClassPath();
        Set<String> writtenClassPaths = new LinkedHashSet<>();

        try {
            Path parent = outputCsvPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (
                    BufferedWriter writer = Files.newBufferedWriter(outputCsvPath, StandardCharsets.UTF_8);
                    CSVPrinter printer = new CSVPrinter(
                            writer,
                            CSVFormat.DEFAULT.builder().setHeader(HEADER).build()
                    )
            ) {
                for (RefactoringMetricsRecord record : records) {
                    Map<String, String> existingRow = existingRowsByClassPath.get(record.classPath());
                    if (existingRow != null) {
                        printer.printRecord(rowValues(existingRow));
                        writtenClassPaths.add(record.classPath());
                        System.out.println("[CSV-MERGE] preserved existing row: " + record.classPath());
                    } else {
                        printer.printRecord(newRowValues(record));
                        writtenClassPaths.add(record.classPath());
                        System.out.println("[CSV-MERGE] added new row: " + record.classPath());
                    }
                }

                for (Map.Entry<String, Map<String, String>> existingEntry : existingRowsByClassPath.entrySet()) {
                    String existingClassPath = existingEntry.getKey();
                    if (!writtenClassPaths.contains(existingClassPath)) {
                        printer.printRecord(rowValues(existingEntry.getValue()));
                        System.out.println("[CSV-MERGE] preserved extra existing row not generated in this run: "
                                + existingClassPath);
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write refactoring metrics csv: " + outputCsvPath, exception);
        }
    }

    private Map<String, Map<String, String>> readExistingRowsByClassPath() {
        Map<String, Map<String, String>> rowsByClassPath = new LinkedHashMap<>();
        if (!Files.exists(outputCsvPath)) {
            return rowsByClassPath;
        }

        try (
                BufferedReader reader = Files.newBufferedReader(outputCsvPath, StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(reader)
        ) {
            Map<String, Integer> headerMap = parser.getHeaderMap();
            if (!headerMap.containsKey(CLASSPATH_COLUMN)) {
                throw new IllegalStateException("Existing refactoring metrics csv has no 'classpath' column: "
                        + outputCsvPath);
            }

            for (CSVRecord csvRecord : parser) {
                String classPath = csvRecord.get(CLASSPATH_COLUMN);
                if (classPath == null || classPath.isBlank()) {
                    System.out.println("[CSV-MERGE-WARNING] skipped existing row without classpath at CSV line "
                            + csvRecord.getRecordNumber());
                    continue;
                }

                Map<String, String> row = new LinkedHashMap<>();
                for (String column : HEADER) {
                    row.put(column, getValueOrBlank(csvRecord, headerMap, column));
                }

                Map<String, String> previous = rowsByClassPath.putIfAbsent(classPath, row);
                if (previous != null) {
                    System.out.println("[CSV-MERGE-WARNING] duplicate existing classpath ignored after first occurrence: "
                            + classPath);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read existing refactoring metrics csv: " + outputCsvPath, exception);
        }

        return rowsByClassPath;
    }

    private String getValueOrBlank(CSVRecord csvRecord, Map<String, Integer> headerMap, String column) {
        if (!headerMap.containsKey(column)) {
            return "";
        }
        return csvRecord.get(column);
    }

    private List<String> rowValues(Map<String, String> row) {
        List<String> values = new ArrayList<>(HEADER.length);
        for (String column : HEADER) {
            values.add(row.getOrDefault(column, ""));
        }
        return values;
    }

    private List<String> newRowValues(RefactoringMetricsRecord record) {
        return List.of(
                record.classPath(),
                String.valueOf(record.loc()),
                String.valueOf(record.locTouched()),
                String.valueOf(record.revs()),
                String.valueOf(record.fixes()),
                String.valueOf(record.auth()),
                String.valueOf(record.locAdded()),
                String.valueOf(record.maxLocAdded()),
                record.avgLocAddedAsCsv(),
                String.valueOf(record.churn()),
                String.valueOf(record.maxChurn()),
                record.avgChurnAsCsv(),
                String.valueOf(record.changeSetSize()),
                String.valueOf(record.maxChangeSet()),
                record.avgChangeSetAsCsv(),
                String.valueOf(record.age()),
                record.weightedAgeAsCsv(),
                String.valueOf(record.commentLines()),
                String.valueOf(record.distinctSmellTypes()),
                String.valueOf(record.nestingDepth()),
                String.valueOf(record.decisionPoints()),
                "",
                String.valueOf(record.pmdSmells())
        );
    }
}
