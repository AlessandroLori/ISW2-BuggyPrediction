package it.university.avro.weka.service;

import it.university.avro.weka.config.WekaFeatureSelectionConfiguration;
import it.university.avro.weka.domain.ClassifierOption;
import it.university.avro.weka.domain.FeatureSelectionReport;
import it.university.avro.weka.domain.SearchDirection;
import it.university.avro.weka.io.FeatureSelectionReportWriter;
import it.university.avro.weka.io.TrainingInstancesLoader;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.WrapperSubsetEval;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WekaFeatureSelectionService {

    private final TrainingInstancesLoader instancesLoader;
    private final FeatureSelectionReportWriter reportWriter;
    private final WekaFeatureSelectionConfiguration configuration;

    public WekaFeatureSelectionService(
            final TrainingInstancesLoader instancesLoader,
            final FeatureSelectionReportWriter reportWriter,
            final WekaFeatureSelectionConfiguration configuration
    ) {
        this.instancesLoader = instancesLoader;
        this.reportWriter = reportWriter;
        this.configuration = configuration;
    }

    public void run() throws Exception {
        final Instances dataset = instancesLoader.load(configuration.inputCsvPath());
        final List<FeatureSelectionReport> reports = new ArrayList<>();


        for (SearchDirection direction : SearchDirection.values()) {
            for (ClassifierOption classifier : ClassifierOption.values()) {
                final FeatureSelectionReport report = executeSingleRun(dataset, direction, classifier);
                reports.add(report);
                System.out.println("Generated feature-selection report: " + report.fileName());
            }
        }

        /*
        for (SearchDirection direction : SearchDirection.values()) {
            for (ClassifierOption classifier : ClassifierOption.values()) {
                if (classifier == ClassifierOption.RANDOM_FOREST) {
                    continue;
                }

                final FeatureSelectionReport report = executeSingleRun(dataset, direction, classifier);
                reports.add(report);
                System.out.println("Generated feature-selection report: " + report.fileName());
            }
        }
        */

        reportWriter.writeAll(configuration.outputDirectory(), reports);
        System.out.println("Generated feature-selection output directory: " + configuration.outputDirectory());
    }

    private FeatureSelectionReport executeSingleRun(
            final Instances dataset,
            final SearchDirection direction,
            final ClassifierOption classifier
    ) throws Exception {
        final AttributeSelection fullSelection = newAttributeSelection(direction, classifier);
        fullSelection.SelectAttributes(new Instances(dataset));

        final int[] selectedAttributes = fullSelection.selectedAttributes();
        final CrossValidationSummary crossValidationSummary =
                executeOuterCrossValidation(dataset, direction, classifier);

        final String fileName = direction.outputPrefix() + classifier.outputName() + ".txt";
        final String content = buildReportContent(
                dataset,
                direction,
                classifier,
                selectedAttributes,
                fullSelection.toResultsString(),
                crossValidationSummary
        );

        return new FeatureSelectionReport(fileName, content);
    }

    private AttributeSelection newAttributeSelection(
            final SearchDirection direction,
            final ClassifierOption classifier
    ) throws Exception {
        final AttributeSelection selection = new AttributeSelection();
        selection.setEvaluator(buildWrapperEvaluator(classifier));
        selection.setSearch(direction.buildSearch());
        return selection;
    }

    private WrapperSubsetEval buildWrapperEvaluator(final ClassifierOption classifier) throws Exception {
        final WrapperSubsetEval evaluator = new WrapperSubsetEval();
        evaluator.setClassifier(classifier.buildClassifier());
        evaluator.setFolds(configuration.wrapperInternalFolds());
        evaluator.setSeed(configuration.wrapperSeed());
        return evaluator;
    }

    private CrossValidationSummary executeOuterCrossValidation(
            final Instances dataset,
            final SearchDirection direction,
            final ClassifierOption classifier
    ) throws Exception {
        final int folds = configuration.outerCrossValidationFolds();
        final int seed = configuration.outerCrossValidationSeed();

        final Instances randomized = new Instances(dataset);
        randomized.randomize(new Random(seed));
        if (randomized.classAttribute().isNominal()) {
            randomized.stratify(folds);
        }

        final int[] attributeSelectionCounts = new int[randomized.numAttributes()];
        final List<String> foldDetails = new ArrayList<>();

        for (int foldIndex = 0; foldIndex < folds; foldIndex++) {
            final Instances trainingFold = randomized.trainCV(folds, foldIndex);
            trainingFold.setClassIndex(randomized.classIndex());

            final AttributeSelection foldSelection = newAttributeSelection(direction, classifier);
            foldSelection.SelectAttributes(trainingFold);

            final List<Integer> selectedPredictiveAttributes = IntStream.of(foldSelection.selectedAttributes())
                    .filter(index -> index != randomized.classIndex())
                    .sorted()
                    .boxed()
                    .toList();

            for (int attributeIndex : selectedPredictiveAttributes) {
                attributeSelectionCounts[attributeIndex]++;
            }

            foldDetails.add(buildFoldDetailLine(foldIndex + 1, randomized, selectedPredictiveAttributes));
        }

        final String summaryTable = buildCrossValidationSummaryTable(randomized, attributeSelectionCounts, folds, seed);
        final String foldBreakdown = String.join(System.lineSeparator(), foldDetails);

        return new CrossValidationSummary(summaryTable, foldBreakdown);
    }

    private String buildFoldDetailLine(
            final int foldNumber,
            final Instances dataset,
            final List<Integer> selectedPredictiveAttributes
    ) {
        if (selectedPredictiveAttributes.isEmpty()) {
            return "Fold " + foldNumber + ": no predictive attributes selected";
        }

        final String names = selectedPredictiveAttributes.stream()
                .map(index -> dataset.attribute(index).name())
                .collect(Collectors.joining(", "));

        return "Fold " + foldNumber + ": " + names;
    }

    private String buildCrossValidationSummaryTable(
            final Instances dataset,
            final int[] attributeSelectionCounts,
            final int folds,
            final int seed
    ) {
        final List<String> lines = new ArrayList<>();
        lines.add("=== Attribute selection " + folds + " fold cross-validation (stratified), seed: " + seed + " ===");
        lines.add("");
        lines.add("number of folds (%)  attribute");

        for (int attributeIndex = 0; attributeIndex < dataset.numAttributes(); attributeIndex++) {
            if (attributeIndex == dataset.classIndex()) {
                continue;
            }

            final int count = attributeSelectionCounts[attributeIndex];
            final int percentage = (int) Math.round((count * 100.0) / folds);

            lines.add(String.format(
                    "%12d(%3d %%) %5d %s",
                    count,
                    percentage,
                    attributeIndex + 1,
                    dataset.attribute(attributeIndex).name()
            ));
        }

        return String.join(System.lineSeparator(), lines);
    }

    private String buildReportContent(
            final Instances dataset,
            final SearchDirection direction,
            final ClassifierOption classifier,
            final int[] selectedAttributes,
            final String fullSelectionResults,
            final CrossValidationSummary crossValidationSummary
    ) {
        final String selectedAttributeSummary = buildSelectedAttributeSummary(dataset, selectedAttributes);

        return String.join(
                System.lineSeparator(),
                "Feature Selection Report",
                "========================",
                "Input CSV: " + configuration.inputCsvPath(),
                "Search method: GreedyStepwise " + direction.outputPrefix(),
                "Attribute evaluator: WrapperSubsetEval",
                "Wrapper classifier: " + classifier.outputName(),
                "Wrapper internal folds: " + configuration.wrapperInternalFolds(),
                "Wrapper internal seed: " + configuration.wrapperSeed(),
                "Outer cross-validation folds: " + configuration.outerCrossValidationFolds(),
                "Outer cross-validation seed: " + configuration.outerCrossValidationSeed(),
                "Class attribute: " + dataset.classAttribute().name(),
                "",
                "Selected attributes on full training set",
                "---------------------------------------",
                selectedAttributeSummary,
                "",
                "Weka full-selection output",
                "--------------------------",
                fullSelectionResults == null ? "" : fullSelectionResults.strip(),
                "",
                "Weka 10-fold cross-validation output",
                "------------------------------------",
                crossValidationSummary.summaryTable(),
                "",
                "Fold-by-fold selected attributes",
                "--------------------------------",
                crossValidationSummary.foldBreakdown(),
                ""
        );
    }

    private String buildSelectedAttributeSummary(final Instances dataset, final int[] selectedAttributes) {
        final List<Integer> featureIndexes = IntStream.of(selectedAttributes)
                .filter(index -> index != dataset.classIndex())
                .boxed()
                .toList();

        if (featureIndexes.isEmpty()) {
            return "No predictive attributes were selected.";
        }

        final String selectedLines = featureIndexes.stream()
                .map(index -> "- [" + (index + 1) + "] " + dataset.attribute(index).name())
                .collect(Collectors.joining(System.lineSeparator()));

        return "Selected predictive attributes: " + featureIndexes.size()
                + System.lineSeparator()
                + selectedLines;
    }

    private record CrossValidationSummary(
            String summaryTable,
            String foldBreakdown
    ) {
    }
}