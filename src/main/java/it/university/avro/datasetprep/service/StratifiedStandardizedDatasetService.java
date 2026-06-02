package it.university.avro.datasetprep.service;

import it.university.avro.common.ApplicationLog;

import it.university.avro.datasetprep.config.DatasetPreparationConfiguration;
import it.university.avro.datasetprep.csv.TabularCsvReader;
import it.university.avro.datasetprep.csv.TabularCsvWriter;
import it.university.avro.datasetprep.domain.TabularDataset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StratifiedStandardizedDatasetService {

    private final TabularCsvReader csvReader;
    private final TabularCsvWriter csvWriter;
    private final DatasetPreparationConfiguration configuration;

    public StratifiedStandardizedDatasetService(
            final TabularCsvReader csvReader,
            final TabularCsvWriter csvWriter,
            final DatasetPreparationConfiguration configuration
    ) {
        this.csvReader = csvReader;
        this.csvWriter = csvWriter;
        this.configuration = configuration;
    }

    public void prepare() {
        final TabularDataset dataset = csvReader.read(configuration.inputCsvPath());
        validateDataset(dataset);

        final List<String> numericHeaders =
                resolveNumericHeaders(dataset.headers(), configuration.excludedColumns());

        final Map<String, ColumnStandardization> standardizationModel =
                fitStandardizationModel(dataset.rows(), numericHeaders);

        final TabularDataset standardizedDataset = transformDataset(
                dataset.headers(),
                dataset.rows(),
                numericHeaders,
                standardizationModel
        );

        final TabularDataset shuffledDataset = shuffleDataset(standardizedDataset, configuration.randomSeed());

        csvWriter.write(standardizedDataset, configuration.standardizedOutputCsvPath());
        csvWriter.write(shuffledDataset, configuration.shuffledOutputCsvPath());

        printSummary(
                configuration.inputCsvPath(),
                configuration.standardizedOutputCsvPath(),
                configuration.shuffledOutputCsvPath(),
                dataset.rows().size(),
                numericHeaders.size()
        );
    }

    private void validateDataset(final TabularDataset dataset) {
        if (dataset.rows().isEmpty()) {
            throw new IllegalStateException("Input csv is empty: " + configuration.inputCsvPath());
        }

        final Set<String> uniqueHeaders = Set.copyOf(dataset.headers());
        if (uniqueHeaders.size() != dataset.headers().size()) {
            throw new IllegalStateException("Input csv contains duplicate headers: " + configuration.inputCsvPath());
        }
    }

    private List<String> resolveNumericHeaders(
            final List<String> headers,
            final Set<String> excludedColumns
    ) {
        final List<String> numericHeaders = new ArrayList<>();

        for (String header : headers) {
            final String normalizedHeader = header.toLowerCase(Locale.ROOT);
            if (!excludedColumns.contains(normalizedHeader)) {
                numericHeaders.add(header);
            }
        }

        if (numericHeaders.isEmpty()) {
            throw new IllegalStateException("No numeric columns found after exclusions");
        }

        return List.copyOf(numericHeaders);
    }

    private Map<String, ColumnStandardization> fitStandardizationModel(
            final List<Map<String, String>> rows,
            final List<String> numericHeaders
    ) {
        final Map<String, ColumnStandardization> model = new LinkedHashMap<>();

        for (String header : numericHeaders) {
            double squaredValueSum = 0.0d;
            int count = 0;

            for (Map<String, String> row : rows) {
                final double value = parseNumericValue(row.get(header), header);
                squaredValueSum += value * value;
                count++;
            }

            if (count == 0) {
                throw new IllegalStateException("Unable to compute scaling model for column: " + header);
            }

            final double variance = squaredValueSum / count;
            final double standardDeviation = Math.sqrt(variance);
            model.put(header, new ColumnStandardization(standardDeviation));
        }

        return model;
    }

    private TabularDataset transformDataset(
            final List<String> headers,
            final List<Map<String, String>> rows,
            final List<String> numericHeaders,
            final Map<String, ColumnStandardization> standardizationModel
    ) {
        final Set<String> numericHeaderSet = Set.copyOf(numericHeaders);
        final List<Map<String, String>> transformedRows = new ArrayList<>(rows.size());

        for (Map<String, String> row : rows) {
            final Map<String, String> transformedRow = new LinkedHashMap<>();

            for (String header : headers) {
                if (numericHeaderSet.contains(header)) {
                    final double rawValue = parseNumericValue(row.get(header), header);
                    final ColumnStandardization standardization = standardizationModel.get(header);
                    final double standardizedValue = standardization.standardize(rawValue);
                    transformedRow.put(header, formatDouble(standardizedValue));
                } else {
                    transformedRow.put(header, row.getOrDefault(header, ""));
                }
            }

            transformedRows.add(transformedRow);
        }

        return new TabularDataset(headers, transformedRows);
    }

    private TabularDataset shuffleDataset(final TabularDataset dataset, final long seed) {
        final List<IndexedRow> indexedRows = new ArrayList<>();
        for (int index = 0; index < dataset.rows().size(); index++) {
            indexedRows.add(new IndexedRow(dataset.rows().get(index), deterministicShuffleKey(dataset.rows().get(index), seed, index)));
        }

        indexedRows.sort(Comparator.comparing(IndexedRow::shuffleKey));
        final List<Map<String, String>> shuffledRows = indexedRows.stream()
                .map(IndexedRow::row)
                .toList();
        return new TabularDataset(dataset.headers(), shuffledRows);
    }

    private String deterministicShuffleKey(
            final Map<String, String> row,
            final long seed,
            final int originalIndex
    ) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Long.toString(seed).getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(originalIndex).getBytes(StandardCharsets.UTF_8));
            digest.update(row.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available for deterministic dataset shuffling", exception);
        }
    }

    private String bytesToHex(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private double parseNumericValue(final String rawValue, final String header) {
        if (rawValue == null || rawValue.isBlank()) {
            return 0.0d;
        }

        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Column '" + header + "' contains a non-numeric value: '" + rawValue + "'",
                    exception
            );
        }
    }

    private String formatDouble(final double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private void printSummary(
            final Path inputPath,
            final Path datasetPath,
            final Path shuffledDatasetPath,
            final int totalRows,
            final int transformedNumericColumns
    ) {
        ApplicationLog.info("Input csv: " + inputPath);
        ApplicationLog.info("Numeric columns transformed with x' = x / sigma: " + transformedNumericColumns);
        ApplicationLog.info("Generated dataset csv: " + datasetPath + " | rows=" + totalRows);
        ApplicationLog.info("Generated shuffled dataset csv: " + shuffledDatasetPath + " | rows=" + totalRows);
        ApplicationLog.info("Shuffle seed: " + configuration.randomSeed());
    }

    private record IndexedRow(Map<String, String> row, String shuffleKey) {
    }

    private record ColumnStandardization(double standardDeviation) {
        private double standardize(final double value) {
            if (Double.compare(value, 0.0d) == 0) {
                return 0.0d;
            }
            if (Double.compare(standardDeviation, 0.0d) == 0) {
                return 0.0d;
            }
            return value / standardDeviation;
        }
    }
}
