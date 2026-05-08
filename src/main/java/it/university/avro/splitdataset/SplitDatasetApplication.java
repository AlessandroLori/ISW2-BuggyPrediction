package it.university.avro.splitdataset;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SplitDatasetApplication {

    private static final Path DEFAULT_INPUT = Path.of("output", "Dataset.csv");
    private static final Path DATASET_C_OUTPUT = Path.of("output", "DatasetC.csv");
    private static final Path DATASET_B_PLUS_OUTPUT = Path.of("output", "DatasetBplus.csv");
    private static final Path DATASET_B_OUTPUT = Path.of("output", "DatasetB.csv");

    private static final double ZERO_EPSILON = 1.0E-12;

    private SplitDatasetApplication() {
        // Utility class.
    }

    public static void main(String[] args) throws IOException {
        Path inputPath = args.length > 0 ? Path.of(args[0]) : DEFAULT_INPUT;

        splitDataset(inputPath);

        System.out.println("Generated: " + DATASET_C_OUTPUT);
        System.out.println("Generated: " + DATASET_B_PLUS_OUTPUT);
        System.out.println("Generated: " + DATASET_B_OUTPUT);
    }

    private static void splitDataset(Path inputPath) throws IOException {
        validateInput(inputPath);
        Files.createDirectories(DATASET_C_OUTPUT.getParent());

        try (
                BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(reader);
                BufferedWriter datasetCWriter = Files.newBufferedWriter(DATASET_C_OUTPUT, StandardCharsets.UTF_8);
                BufferedWriter datasetBPlusWriter = Files.newBufferedWriter(DATASET_B_PLUS_OUTPUT, StandardCharsets.UTF_8);
                BufferedWriter datasetBWriter = Files.newBufferedWriter(DATASET_B_OUTPUT, StandardCharsets.UTF_8)
        ) {
            List<String> headers = parser.getHeaderNames();
            Map<String, Integer> headerMap = parser.getHeaderMap();

            String smellColumnName = findSmellColumnName(headerMap);
            String distinctSmellTypesColumnName = findDistinctSmellTypesColumnName(headerMap);

            try (
                    CSVPrinter datasetCPrinter = new CSVPrinter(
                            datasetCWriter,
                            CSVFormat.DEFAULT.builder()
                                    .setHeader(headers.toArray(String[]::new))
                                    .build()
                    );
                    CSVPrinter datasetBPlusPrinter = new CSVPrinter(
                            datasetBPlusWriter,
                            CSVFormat.DEFAULT.builder()
                                    .setHeader(headers.toArray(String[]::new))
                                    .build()
                    );
                    CSVPrinter datasetBPrinter = new CSVPrinter(
                            datasetBWriter,
                            CSVFormat.DEFAULT.builder()
                                    .setHeader(headers.toArray(String[]::new))
                                    .build()
                    )
            ) {
                int rowsC = 0;
                int rowsBPlus = 0;

                for (CSVRecord record : parser) {
                    double smellValue = parseNumericValue(record, smellColumnName);

                    if (isZero(smellValue)) {
                        printOriginalRecord(datasetCPrinter, headers, record);
                        rowsC++;
                    } else if (smellValue > ZERO_EPSILON) {
                        printOriginalRecord(datasetBPlusPrinter, headers, record);
                        printRecordWithZeroSmellColumns(
                                datasetBPrinter,
                                headers,
                                record,
                                smellColumnName,
                                distinctSmellTypesColumnName
                        );
                        rowsBPlus++;
                    } else {
                        throw new IllegalStateException(
                                "Negative smell value found at CSV row "
                                        + record.getRecordNumber()
                                        + ": "
                                        + smellValue
                        );
                    }
                }

                System.out.println("Input rows processed: " + (rowsC + rowsBPlus));
                System.out.println("DatasetC rows, smells = 0: " + rowsC);
                System.out.println("DatasetBplus rows, smells > 0: " + rowsBPlus);
                System.out.println("DatasetB rows, smells and distinct smell types forced to 0: " + rowsBPlus);
            }
        }
    }

    private static void validateInput(Path inputPath) {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input file not found: " + inputPath);
        }

        if (!Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("Input path is not a regular file: " + inputPath);
        }
    }

    private static String findSmellColumnName(Map<String, Integer> headerMap) {
        for (String header : headerMap.keySet()) {
            String normalized = normalizeHeader(header);

            if ("nsmells".equals(normalized)
                    || "smells".equals(normalized)
                    || "numberofsmells".equals(normalized)
                    || "numberofcodesmells".equals(normalized)) {
                return header;
            }
        }

        throw new IllegalStateException(
                "Smell column not found. Expected one of: nsmells, smells, number_of_smells"
        );
    }

    private static String findDistinctSmellTypesColumnName(Map<String, Integer> headerMap) {
        for (String header : headerMap.keySet()) {
            String normalized = normalizeHeader(header);

            if ("distinctsmelltypes".equals(normalized)
                    || "distinctsmelltype".equals(normalized)
                    || "distinctpmdsmelltypes".equals(normalized)) {
                return header;
            }
        }

        throw new IllegalStateException(
                "Distinct smell types column not found. Expected DISTINCT_SMELL_TYPES or distinct_smell_types"
        );
    }

    private static String normalizeHeader(String header) {
        return header
                .toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    private static double parseNumericValue(CSVRecord record, String columnName) {
        String rawValue = record.get(columnName);

        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalStateException(
                    "Blank value for column "
                            + columnName
                            + " at CSV row "
                            + record.getRecordNumber()
            );
        }

        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Invalid numeric value for column "
                            + columnName
                            + " at CSV row "
                            + record.getRecordNumber()
                            + ": "
                            + rawValue,
                    exception
            );
        }
    }

    private static boolean isZero(double value) {
        return Math.abs(value) <= ZERO_EPSILON;
    }

    private static void printOriginalRecord(
            CSVPrinter printer,
            List<String> headers,
            CSVRecord record
    ) throws IOException {
        for (String header : headers) {
            printer.print(record.get(header));
        }

        printer.println();
    }

    private static void printRecordWithZeroSmellColumns(
            CSVPrinter printer,
            List<String> headers,
            CSVRecord record,
            String smellColumnName,
            String distinctSmellTypesColumnName
    ) throws IOException {
        for (String header : headers) {
            if (header.equals(smellColumnName) || header.equals(distinctSmellTypesColumnName)) {
                printer.print("0");
            } else {
                printer.print(record.get(header));
            }
        }

        printer.println();
    }
}