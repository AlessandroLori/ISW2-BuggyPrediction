package it.university.avro.correlation;

import it.university.avro.common.ApplicationLog;

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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CorrelationAnalysisApplication {

    private static final String OUTPUT_DIRECTORY = "output";

    private static final Path DEFAULT_DATASET_A = Path.of(OUTPUT_DIRECTORY, "Dataset.csv");
    private static final Path DEFAULT_DATASET_B = Path.of(OUTPUT_DIRECTORY, "DatasetB.csv");
    private static final Path DEFAULT_DATASET_C = Path.of(OUTPUT_DIRECTORY, "DatasetC.csv");
    private static final Path DEFAULT_OUTPUT = Path.of(OUTPUT_DIRECTORY, "CorrelationAnalysis.csv");

    private static final String BUGGY_COLUMN = "BUGGY";
    private static final String NSMELLS_COLUMN = "nsmells";
    private static final double SIGNIFICANCE_ALPHA = 0.05;
    private static final double NUMERIC_EPSILON = 1.0E-12;

    private static final List<String> IDENTIFIER_COLUMNS = List.of("version", "classpath");

    private static final List<String> METRIC_COLUMNS = List.of(
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
            NSMELLS_COLUMN,
            "DISTINCT_SMELL_TYPES"
    );

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat(
            "0.######",
            DecimalFormatSymbols.getInstance(Locale.US)
    );

    private CorrelationAnalysisApplication() {
        // Utility class.
    }

    public static void main(String[] args) throws IOException {
        Path datasetAPath = args.length > 0 ? Path.of(args[0]) : DEFAULT_DATASET_A;
        Path datasetBPath = args.length > 1 ? Path.of(args[1]) : DEFAULT_DATASET_B;
        Path datasetCPath = args.length > 2 ? Path.of(args[2]) : DEFAULT_DATASET_C;
        Path outputPath = args.length > 3 ? Path.of(args[3]) : DEFAULT_OUTPUT;

        Dataset datasetA = readDataset("Dataset", datasetAPath);
        Dataset datasetB = readDataset("DatasetB", datasetBPath);
        Dataset datasetC = readDataset("DatasetC", datasetCPath);

        List<String> metrics = resolveMetricColumns(datasetA.headers());
        writeCorrelationReport(datasetA, datasetB, datasetC, metrics, outputPath);

        ApplicationLog.info("Generated correlation analysis csv: " + outputPath);
        ApplicationLog.info("Dataset rows: " + datasetA.size());
        ApplicationLog.info("DatasetB rows: " + datasetB.size());
        ApplicationLog.info("DatasetC rows: " + datasetC.size());
    }

    private static Dataset readDataset(String name, Path path) throws IOException {
        validateInputFile(path);

        try (
                BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(reader)
        ) {
            List<String> headers = parser.getHeaderNames();
            List<Map<String, String>> rows = new ArrayList<>();

            for (CSVRecord csvRecord : parser) {
                Map<String, String> row = new HashMap<>();
                for (String header : headers) {
                    row.put(header, csvRecord.get(header));
                }
                rows.add(row);
            }

            return new Dataset(name, headers, rows);
        }
    }

    private static void validateInputFile(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Input file not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Input path is not a regular file: " + path);
        }
    }

    private static List<String> resolveMetricColumns(List<String> headers) {
        Set<String> headerSet = new HashSet<>(headers);
        List<String> metrics = new ArrayList<>();

        for (String metric : METRIC_COLUMNS) {
            if (headerSet.contains(metric)) {
                metrics.add(metric);
            }
        }

        if (metrics.isEmpty()) {
            for (String header : headers) {
                if (!IDENTIFIER_COLUMNS.contains(header) && !BUGGY_COLUMN.equalsIgnoreCase(header)) {
                    metrics.add(header);
                }
            }
        }

        if (!metrics.contains(NSMELLS_COLUMN)) {
            throw new IllegalStateException("Required smell metric column not found: " + NSMELLS_COLUMN);
        }

        return metrics;
    }

    private static void writeCorrelationReport(
            Dataset datasetA,
            Dataset datasetB,
            Dataset datasetC,
            List<String> metrics,
            Path outputPath
    ) throws IOException {
        Files.createDirectories(outputPath.getParent());

        try (
                BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8);
                CSVPrinter printer = new CSVPrinter(
                        writer,
                        CSVFormat.DEFAULT.builder()
                                .setHeader("Variable", "Dataset", "DatasetB", "DatasetC", "Nsmells", "Defectiveness")
                                .build()
                )
        ) {
            printer.printRecord(
                    "Size",
                    Integer.toString(datasetA.size()),
                    Integer.toString(datasetB.size()),
                    Integer.toString(datasetC.size()),
                    "",
                    formatNumber(datasetA.defectivenessRate())
            );

            double[] smellValues = datasetA.numericColumn(NSMELLS_COLUMN);
            double[] defectivenessValues = datasetA.defectivenessColumn();

            for (String metric : metrics) {
                double[] metricValues = datasetA.numericColumn(metric);
                String nsmellsCorrelation = isSameColumn(metric, NSMELLS_COLUMN)
                        ? "-"
                        : formatCorrelationWithSignificance(metricValues, smellValues);
                String defectivenessCorrelation = formatCorrelationWithSignificance(metricValues, defectivenessValues);

                printer.printRecord(
                        metric,
                        formatNumber(datasetA.mean(metric)),
                        formatNumber(datasetB.mean(metric)),
                        formatNumber(datasetC.mean(metric)),
                        nsmellsCorrelation,
                        defectivenessCorrelation
                );
            }
        }
    }

    private static boolean isSameColumn(String left, String right) {
        return left.equalsIgnoreCase(right);
    }

    private static String formatCorrelationWithSignificance(double[] left, double[] right) {
        CorrelationResult result = spearmanCorrelation(left, right);
        if (!result.isDefined()) {
            return "NA";
        }

        String formattedCorrelation = formatNumber(result.value());
        if (!Double.isNaN(result.pValue()) && result.pValue() < SIGNIFICANCE_ALPHA) {
            return formattedCorrelation + "*";
        }
        return formattedCorrelation;
    }

    private static CorrelationResult spearmanCorrelation(double[] left, double[] right) {
        validateSameLength(left, right);
        if (left.length < 3) {
            return CorrelationResult.undefined();
        }

        double[] leftRanks = ranks(left);
        double[] rightRanks = ranks(right);
        double rho = pearsonCorrelation(leftRanks, rightRanks);

        if (Double.isNaN(rho)) {
            return CorrelationResult.undefined();
        }

        double pValue = spearmanApproximatePValue(rho, left.length);
        return new CorrelationResult(rho, pValue);
    }

    private static void validateSameLength(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException(
                    "Correlation vectors have different sizes: " + left.length + " vs " + right.length
            );
        }
    }

    private static double[] ranks(double[] values) {
        List<IndexedValue> indexedValues = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            indexedValues.add(new IndexedValue(index, values[index]));
        }

        indexedValues.sort((left, right) -> Double.compare(left.value(), right.value()));

        double[] ranks = new double[values.length];
        int start = 0;
        while (start < indexedValues.size()) {
            int end = start + 1;
            while (end < indexedValues.size()
                    && nearlyEqual(indexedValues.get(start).value(), indexedValues.get(end).value())) {
                end++;
            }

            double averageRank = ((start + 1.0) + end) / 2.0;
            for (int index = start; index < end; index++) {
                ranks[indexedValues.get(index).index()] = averageRank;
            }
            start = end;
        }

        return ranks;
    }

    private static boolean nearlyEqual(double left, double right) {
        return Math.abs(left - right) <= NUMERIC_EPSILON;
    }

    private static double pearsonCorrelation(double[] left, double[] right) {
        double leftMean = mean(left);
        double rightMean = mean(right);
        double numerator = 0.0;
        double leftSquared = 0.0;
        double rightSquared = 0.0;

        for (int index = 0; index < left.length; index++) {
            double leftCentered = left[index] - leftMean;
            double rightCentered = right[index] - rightMean;
            numerator += leftCentered * rightCentered;
            leftSquared += leftCentered * leftCentered;
            rightSquared += rightCentered * rightCentered;
        }

        if (leftSquared <= NUMERIC_EPSILON || rightSquared <= NUMERIC_EPSILON) {
            return Double.NaN;
        }

        return numerator / Math.sqrt(leftSquared * rightSquared);
    }

    private static double spearmanApproximatePValue(double rho, int sampleSize) {
        if (sampleSize < 3) {
            return Double.NaN;
        }

        double boundedRho = Math.max(-1.0, Math.min(1.0, rho));
        if (Math.abs(1.0 - Math.abs(boundedRho)) <= NUMERIC_EPSILON) {
            return 0.0;
        }

        int degreesOfFreedom = sampleSize - 2;
        double tStatistic = Math.abs(boundedRho) * Math.sqrt(degreesOfFreedom / (1.0 - boundedRho * boundedRho));
        return twoTailedStudentTPValue(tStatistic, degreesOfFreedom);
    }

    private static double twoTailedStudentTPValue(double tStatistic, int degreesOfFreedom) {
        double x = degreesOfFreedom / (degreesOfFreedom + tStatistic * tStatistic);
        return regularizedBeta(x, degreesOfFreedom / 2.0, 0.5);
    }

    private static double regularizedBeta(double x, double a, double b) {
        if (x < 0.0 || x > 1.0) {
            throw new IllegalArgumentException("x must be in [0, 1], found: " + x);
        }
        if (x == 0.0 || x == 1.0) {
            return x;
        }

        double betaFactor = Math.exp(logGamma(a + b)
                - logGamma(a)
                - logGamma(b)
                + a * Math.log(x)
                + b * Math.log1p(-x));

        if (x < (a + 1.0) / (a + b + 2.0)) {
            return betaFactor * betaContinuedFraction(a, b, x) / a;
        }
        return 1.0 - betaFactor * betaContinuedFraction(b, a, 1.0 - x) / b;
    }

    private static double betaContinuedFraction(double leftShape, double rightShape, double argument) {
        final int maxIterations = 10_000;
        final double epsilon = 3.0E-14;
        final double fpMin = 1.0E-300;

        double qab = leftShape + rightShape;
        double qap = leftShape + 1.0;
        double qam = leftShape - 1.0;
        double c = 1.0;
        double d = 1.0 - qab * argument / qap;

        if (Math.abs(d) < fpMin) {
            d = fpMin;
        }

        d = 1.0 / d;
        double h = d;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            int m2 = 2 * iteration;

            double aa = iteration * (rightShape - iteration) * argument / ((qam + m2) * (leftShape + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < fpMin) {
                d = fpMin;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < fpMin) {
                c = fpMin;
            }
            d = 1.0 / d;
            h *= d * c;

            aa = -(leftShape + iteration) * (qab + iteration) * argument / ((leftShape + m2) * (qap + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < fpMin) {
                d = fpMin;
            }
            c = 1.0 + aa / c;
            if (Math.abs(c) < fpMin) {
                c = fpMin;
            }
            d = 1.0 / d;
            double delta = d * c;
            h *= delta;

            if (Math.abs(delta - 1.0) < epsilon) {
                return h;
            }
        }

        throw new IllegalStateException("Beta continued fraction did not converge");
    }

    private static double logGamma(double x) {
        double[] coefficients = {
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716E-6,
                1.5056327351493116E-7
        };

        if (x < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * x)) - logGamma(1.0 - x);
        }

        double adjustedX = x - 1.0;
        double accumulator = 0.99999999999980993;
        for (int index = 0; index < coefficients.length; index++) {
            accumulator += coefficients[index] / (adjustedX + index + 1.0);
        }

        double t = adjustedX + coefficients.length - 0.5;
        return 0.5 * Math.log(2.0 * Math.PI)
                + (adjustedX + 0.5) * Math.log(t)
                - t
                + Math.log(accumulator);
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }

        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value)) {
            return "NA";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        synchronized (NUMBER_FORMAT) {
            return NUMBER_FORMAT.format(value);
        }
    }

    private static final class Dataset {
        private final String name;
        private final List<String> headers;
        private final List<Map<String, String>> rows;

        private Dataset(String name, List<String> headers, List<Map<String, String>> rows) {
            this.name = name;
            this.headers = List.copyOf(headers);
            this.rows = List.copyOf(rows);
            validateRequiredColumns();
        }

        private void validateRequiredColumns() {
            if (!headers.contains(BUGGY_COLUMN)) {
                throw new IllegalStateException(name + " does not contain required column: " + BUGGY_COLUMN);
            }
            if (!headers.contains(NSMELLS_COLUMN)) {
                throw new IllegalStateException(name + " does not contain required column: " + NSMELLS_COLUMN);
            }
        }

        private List<String> headers() {
            return headers;
        }

        private int size() {
            return rows.size();
        }

        private double mean(String columnName) {
            return CorrelationAnalysisApplication.mean(numericColumn(columnName));
        }

        private double[] numericColumn(String columnName) {
            if (!headers.contains(columnName)) {
                throw new IllegalStateException(name + " does not contain column: " + columnName);
            }

            double[] values = new double[rows.size()];
            for (int index = 0; index < rows.size(); index++) {
                values[index] = parseDouble(rows.get(index).get(columnName), columnName, index + 2);
            }
            return values;
        }

        private double[] defectivenessColumn() {
            double[] values = new double[rows.size()];
            for (int index = 0; index < rows.size(); index++) {
                values[index] = parseBuggyValue(rows.get(index).get(BUGGY_COLUMN), index + 2);
            }
            return values;
        }

        private double defectivenessRate() {
            return CorrelationAnalysisApplication.mean(defectivenessColumn());
        }

        private double parseDouble(String rawValue, String columnName, int csvLineNumber) {
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalStateException(
                        name + " has blank value for column " + columnName + " at line " + csvLineNumber
                );
            }

            try {
                return Double.parseDouble(rawValue.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                        name + " has invalid numeric value for column "
                                + columnName
                                + " at line "
                                + csvLineNumber
                                + ": "
                                + rawValue,
                        exception
                );
            }
        }

        private double parseBuggyValue(String rawValue, int csvLineNumber) {
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalStateException(name + " has blank BUGGY value at line " + csvLineNumber);
            }

            String normalizedValue = rawValue.trim().toUpperCase(Locale.ROOT);
            if ("YES".equals(normalizedValue)) {
                return 1.0;
            }
            if ("NO".equals(normalizedValue)) {
                return 0.0;
            }

            throw new IllegalStateException(
                    name + " has invalid BUGGY value at line " + csvLineNumber + ": " + rawValue
            );
        }
    }

    private record IndexedValue(int index, double value) {
    }

    private record CorrelationResult(double value, double pValue) {
        private static CorrelationResult undefined() {
            return new CorrelationResult(Double.NaN, Double.NaN);
        }

        private boolean isDefined() {
            return !Double.isNaN(value);
        }
    }
}
