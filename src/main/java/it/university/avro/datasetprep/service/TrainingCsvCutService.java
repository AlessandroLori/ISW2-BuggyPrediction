package it.university.avro.dataset.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TrainingCsvCutService {

    private static final Set<String> COLUMNS_TO_DROP = Set.of("version", "classpath");

    public void generateCuttedTrainingCsv(final Path inputCsvPath, final Path outputCsvPath) throws IOException {
        validatePaths(inputCsvPath, outputCsvPath);

        try (
                Reader reader = Files.newBufferedReader(inputCsvPath, StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(reader)
        ) {
            final List<String> originalHeaders = parser.getHeaderNames();
            validateHeaders(originalHeaders);

            final List<String> keptHeaders = extractKeptHeaders(originalHeaders);

            createParentDirectoryIfNeeded(outputCsvPath);

            try (
                    Writer writer = Files.newBufferedWriter(outputCsvPath, StandardCharsets.UTF_8);
                    CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)
            ) {
                printer.printRecord(keptHeaders);

                for (CSVRecord record : parser) {
                    final List<String> projectedRow = projectRow(record, keptHeaders);
                    printer.printRecord(projectedRow);
                }

                printer.flush();
            }
        }
    }

    private void validatePaths(final Path inputCsvPath, final Path outputCsvPath) {
        if (inputCsvPath == null) {
            throw new IllegalArgumentException("Input CSV path must not be null.");
        }
        if (outputCsvPath == null) {
            throw new IllegalArgumentException("Output CSV path must not be null.");
        }
        if (!Files.exists(inputCsvPath)) {
            throw new IllegalArgumentException("Input CSV does not exist: " + inputCsvPath);
        }
        if (!Files.isRegularFile(inputCsvPath)) {
            throw new IllegalArgumentException("Input CSV is not a regular file: " + inputCsvPath);
        }
    }

    private void validateHeaders(final List<String> originalHeaders) {
        if (originalHeaders == null || originalHeaders.isEmpty()) {
            throw new IllegalStateException("Input CSV has no header.");
        }

        final Set<String> uniqueHeaders = new LinkedHashSet<>(originalHeaders);
        if (uniqueHeaders.size() != originalHeaders.size()) {
            throw new IllegalStateException("Input CSV contains duplicate headers.");
        }

        for (String requiredHeader : COLUMNS_TO_DROP) {
            if (!uniqueHeaders.contains(requiredHeader)) {
                throw new IllegalStateException("Missing required header to cut: " + requiredHeader);
            }
        }
    }

    private List<String> extractKeptHeaders(final List<String> originalHeaders) {
        final List<String> keptHeaders = new ArrayList<>();
        for (String header : originalHeaders) {
            if (!COLUMNS_TO_DROP.contains(header)) {
                keptHeaders.add(header);
            }
        }

        if (keptHeaders.isEmpty()) {
            throw new IllegalStateException("No columns left after cutting version and classpath.");
        }

        return keptHeaders;
    }

    private List<String> projectRow(final CSVRecord record, final List<String> keptHeaders) {
        final List<String> projectedRow = new ArrayList<>(keptHeaders.size());
        for (String header : keptHeaders) {
            projectedRow.add(record.get(header));
        }
        return projectedRow;
    }

    private void createParentDirectoryIfNeeded(final Path outputCsvPath) throws IOException {
        final Path parent = outputCsvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}