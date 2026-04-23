package it.university.avro.datasetprep.service;

import it.university.avro.datasetprep.config.DatasetPreparationConfiguration;
import it.university.avro.datasetprep.csv.TabularCsvReader;
import it.university.avro.datasetprep.csv.TabularCsvWriter;
import it.university.avro.datasetprep.domain.TabularDataset;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

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

        if (dataset.rows().isEmpty()) {
            throw new IllegalStateException("Input csv is empty: " + configuration.inputCsvPath());
        }

        final String buggyHeader = findHeaderIgnoreCase(dataset.headers(), "buggy");
        final List<String> numericHeaders = resolveNumericHeaders(dataset.headers(), configuration.excludedColumns());

        final SplitResult splitResult = stratifiedSplit(dataset, buggyHeader);

        final Map<String, ColumnStandardization> standardizationModel =
                fitStandardizationModel(splitResult.trainingRows(), numericHeaders);

        final TabularDataset trainingDataset = transformDataset(
                dataset.headers(),
                splitResult.trainingRows(),
                numericHeaders,
                standardizationModel
        );

        final TabularDataset testingDataset = transformDataset(
                dataset.headers(),
                splitResult.testingRows(),
                numericHeaders,
                standardizationModel
        );

        csvWriter.write(trainingDataset, configuration.trainingOutputCsvPath());
        csvWriter.write(testingDataset, configuration.testingOutputCsvPath());

        printSummary(
                configuration.inputCsvPath(),
                configuration.trainingOutputCsvPath(),
                configuration.testingOutputCsvPath(),
                dataset.rows().size(),
                trainingDataset.rows(),
                testingDataset.rows(),
                buggyHeader,
                numericHeaders
        );
    }

    private String findHeaderIgnoreCase(final List<String> headers, final String logicalName) {
        for (String header : headers) {
            if (header.equalsIgnoreCase(logicalName)) {
                return header;
            }
        }
        throw new IllegalStateException("Missing required column: " + logicalName);
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

    private SplitResult stratifiedSplit(
            final TabularDataset dataset,
            final String buggyHeader
    ) {
        final Map<String, List<Map<String, String>>> rowsByLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map<String, String> row : dataset.rows()) {
            final String label = row.getOrDefault(buggyHeader, "").trim();
            rowsByLabel.computeIfAbsent(label, unused -> new LinkedList<>()).add(row);
        }

        final List<Map<String, String>> trainingRows = new ArrayList<>();
        final List<Map<String, String>> testingRows = new ArrayList<>();

        final Random masterRandom = new Random(configuration.randomSeed());

        for (Map.Entry<String, List<Map<String, String>>> entry : rowsByLabel.entrySet()) {
            final List<Map<String, String>> labelRows = new ArrayList<>(entry.getValue());
            Collections.shuffle(labelRows, new Random(masterRandom.nextLong()));

            final int testSize = computeTestSize(labelRows.size(), configuration.testRatio());

            testingRows.addAll(labelRows.subList(0, testSize));
            trainingRows.addAll(labelRows.subList(testSize, labelRows.size()));
        }

        Collections.shuffle(trainingRows, new Random(masterRandom.nextLong()));
        Collections.shuffle(testingRows, new Random(masterRandom.nextLong()));

        return new SplitResult(trainingRows, testingRows);
    }

    private int computeTestSize(final int classSize, final double testRatio) {
        if (classSize <= 1) {
            return 0;
        }

        int testSize = (int) Math.round(classSize * testRatio);

        if (testSize <= 0) {
            testSize = 1;
        }
        if (testSize >= classSize) {
            testSize = classSize - 1;
        }

        return testSize;
    }

    private Map<String, ColumnStandardization> fitStandardizationModel(
            final List<Map<String, String>> trainingRows,
            final List<String> numericHeaders
    ) {
        final Map<String, ColumnStandardization> model = new LinkedHashMap<>();

        for (String header : numericHeaders) {
            double sum = 0.0d;
            int count = 0;

            for (Map<String, String> row : trainingRows) {
                final double value = parseNumericValue(row.get(header), header);
                sum += value;
                count++;
            }

            if (count == 0) {
                throw new IllegalStateException("Unable to compute scaling model for column: " + header);
            }

            final double mean = sum / count;

            double squaredDiffSum = 0.0d;
            for (Map<String, String> row : trainingRows) {
                final double value = parseNumericValue(row.get(header), header);
                final double diff = value - mean;
                squaredDiffSum += diff * diff;
            }

            final double variance = squaredDiffSum / count;
            final double stdDev = Math.sqrt(variance);

            model.put(header, new ColumnStandardization(stdDev));
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
        final List<Map<String, String>> transformedRows = new ArrayList<>();

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
            final Path trainPath,
            final Path testPath,
            final int totalRows,
            final List<Map<String, String>> trainingRows,
            final List<Map<String, String>> testingRows,
            final String buggyHeader,
            final List<String> numericHeaders
    ) {
        System.out.println("Input csv: " + inputPath);
        System.out.println("Numeric columns transformed with standardization (z-score): " + numericHeaders.size());
        System.out.println("Training csv: " + trainPath + " | rows=" + trainingRows.size());
        System.out.println("Testing csv: " + testPath + " | rows=" + testingRows.size());
        System.out.println("Total rows processed: " + totalRows);

        printLabelDistribution("TRAIN", trainingRows, buggyHeader);
        printLabelDistribution("TEST", testingRows, buggyHeader);
    }

    private void printLabelDistribution(
            final String splitName,
            final List<Map<String, String>> rows,
            final String buggyHeader
    ) {
        final Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map<String, String> row : rows) {
            final String label = row.getOrDefault(buggyHeader, "").trim();
            counts.merge(label, 1, Integer::sum);
        }

        System.out.println(splitName + " label distribution:");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            final double percentage = rows.isEmpty()
                    ? 0.0d
                    : (entry.getValue() * 100.0d) / rows.size();

            System.out.println(
                    "  " + entry.getKey()
                            + " -> " + entry.getValue()
                            + " (" + String.format(Locale.ROOT, "%.2f", percentage) + "%)"
            );
        }
    }

    private record SplitResult(
            List<Map<String, String>> trainingRows,
            List<Map<String, String>> testingRows
    ) {
    }

    private record ColumnStandardization(
            double standardDeviation
    ) {
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