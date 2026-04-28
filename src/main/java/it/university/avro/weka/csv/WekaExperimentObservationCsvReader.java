package it.university.avro.weka.csv;

import it.university.avro.weka.domain.WekaExperimentObservation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WekaExperimentObservationCsvReader {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "Key_Dataset",
            "Key_Run",
            "Key_Fold",
            "Key_Scheme",
            "Key_Scheme_options",
            "IR_precision",
            "IR_recall",
            "Area_under_ROC",
            "Kappa_statistic"
    );

    public List<WekaExperimentObservation> read(final Path inputCsvPath) {
        try (BufferedReader reader = Files.newBufferedReader(inputCsvPath)) {
            final String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalStateException("Input Weka results csv is empty: " + inputCsvPath);
            }

            final CsvHeader csvHeader = parseHeader(headerLine, inputCsvPath);
            final List<WekaExperimentObservation> observations = new ArrayList<>();

            String line;
            long rowNumber = 1L;
            while ((line = reader.readLine()) != null) {
                rowNumber++;

                if (line.isBlank()) {
                    continue;
                }

                final String[] columns = splitCsvLine(line, csvHeader.columnCount(), rowNumber, inputCsvPath);
                observations.add(new WekaExperimentObservation(
                        requiredValue(columns, csvHeader, "Key_Dataset", rowNumber),
                        parseInteger(columns, csvHeader, "Key_Run", rowNumber),
                        parseInteger(columns, csvHeader, "Key_Fold", rowNumber),
                        requiredValue(columns, csvHeader, "Key_Scheme", rowNumber),
                        requiredValue(columns, csvHeader, "Key_Scheme_options", rowNumber),
                        parseDouble(columns, csvHeader, "IR_precision", rowNumber),
                        parseDouble(columns, csvHeader, "IR_recall", rowNumber),
                        parseDouble(columns, csvHeader, "Area_under_ROC", rowNumber),
                        parseDouble(columns, csvHeader, "Kappa_statistic", rowNumber)
                ));
            }

            if (observations.isEmpty()) {
                throw new IllegalStateException("Input Weka results csv contains no data rows: " + inputCsvPath);
            }

            return List.copyOf(observations);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Weka results csv: " + inputCsvPath, exception);
        }
    }

    private CsvHeader parseHeader(
            final String headerLine,
            final Path inputCsvPath
    ) {
        final String[] headerColumns = headerLine.split(",", -1);
        final Map<String, Integer> indexesByHeader = new LinkedHashMap<>();

        for (int index = 0; index < headerColumns.length; index++) {
            indexesByHeader.put(headerColumns[index].trim(), index);
        }

        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!indexesByHeader.containsKey(requiredHeader)) {
                throw new IllegalStateException(
                        "Missing required column '" + requiredHeader + "' in " + inputCsvPath
                );
            }
        }

        return new CsvHeader(indexesByHeader, headerColumns.length);
    }

    private String[] splitCsvLine(
            final String line,
            final int expectedColumnCount,
            final long rowNumber,
            final Path inputCsvPath
    ) {
        final String[] columns = line.split(",", -1);
        if (columns.length != expectedColumnCount) {
            throw new IllegalStateException(
                    "Unexpected number of columns at row " + rowNumber
                            + " in " + inputCsvPath
                            + ": expected " + expectedColumnCount
                            + " but found " + columns.length
            );
        }
        return columns;
    }

    private String requiredValue(
            final String[] columns,
            final CsvHeader csvHeader,
            final String header,
            final long rowNumber
    ) {
        final int index = csvHeader.indexOf(header);
        final String value = columns[index].trim();
        if (value.isEmpty()) {
            throw new IllegalStateException(
                    "Missing value for column '" + header + "' at row " + rowNumber
            );
        }
        return value;
    }

    private int parseInteger(
            final String[] columns,
            final CsvHeader csvHeader,
            final String header,
            final long rowNumber
    ) {
        final String rawValue = requiredValue(columns, csvHeader, header, rowNumber);
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Invalid integer value '" + rawValue + "' for column '" + header + "' at row " + rowNumber,
                    exception
            );
        }
    }

    private double parseDouble(
            final String[] columns,
            final CsvHeader csvHeader,
            final String header,
            final long rowNumber
    ) {
        final String rawValue = requiredValue(columns, csvHeader, header, rowNumber);
        if (rawValue.equals("?")) {
            throw new IllegalStateException(
                    "Unexpected missing numeric value '?' for column '" + header + "' at row " + rowNumber
            );
        }

        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Invalid numeric value '" + rawValue + "' for column '" + header + "' at row " + rowNumber,
                    exception
            );
        }
    }

    private record CsvHeader(
            Map<String, Integer> indexesByHeader,
            int columnCount
    ) {
        private int indexOf(final String header) {
            final Integer index = indexesByHeader.get(header);
            if (index == null) {
                throw new IllegalStateException("Unknown column: " + header);
            }
            return index;
        }
    }
}
