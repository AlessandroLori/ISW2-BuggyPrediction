package it.university.avro.weka.service;

import it.university.avro.weka.csv.FinalWekaResultsCsvWriter;
import it.university.avro.weka.csv.WekaExperimentObservationCsvReader;
import it.university.avro.weka.domain.FinalWekaResultRecord;
import it.university.avro.weka.domain.WekaConfigurationKey;
import it.university.avro.weka.domain.WekaExperimentObservation;
import it.university.avro.weka.util.WekaConfigurationInterpreter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class WekaResultsAggregationService {

    private final WekaExperimentObservationCsvReader csvReader;
    private final FinalWekaResultsCsvWriter csvWriter;
    private final WekaConfigurationInterpreter configurationInterpreter;

    public WekaResultsAggregationService(
            final WekaExperimentObservationCsvReader csvReader,
            final FinalWekaResultsCsvWriter csvWriter,
            final WekaConfigurationInterpreter configurationInterpreter
    ) {
        this.csvReader = csvReader;
        this.csvWriter = csvWriter;
        this.configurationInterpreter = configurationInterpreter;
    }

    public void generate(final Path inputCsvPath) {
        final List<WekaExperimentObservation> observations = csvReader.read(inputCsvPath);
        final List<FinalWekaResultRecord> finalRecords = aggregate(observations);

        csvWriter.write(finalRecords);

        System.out.println(
                "Generated final Weka results csv: "
                        + csvWriter.outputCsvPath()
                        + " | rows="
                        + finalRecords.size()
        );
    }

    private List<FinalWekaResultRecord> aggregate(final List<WekaExperimentObservation> observations) {
        final Map<WekaConfigurationKey, Map<String, Map<Integer, List<WekaExperimentObservation>>>> grouped =
                groupObservations(observations);

        final List<FinalWekaResultRecord> finalRecords = new ArrayList<>();

        for (Map.Entry<WekaConfigurationKey, Map<String, Map<Integer, List<WekaExperimentObservation>>>>
                configurationEntry : grouped.entrySet()) {
            final WekaConfigurationKey configurationKey = configurationEntry.getKey();
            final List<SchemeAverages> schemeAverages = computeSchemeAverages(
                    configurationKey,
                    configurationEntry.getValue()
            );

            finalRecords.add(buildFinalRecord(configurationKey, schemeAverages));
        }

        finalRecords.sort(finalRecordComparator());
        return List.copyOf(finalRecords);
    }

    private Map<WekaConfigurationKey, Map<String, Map<Integer, List<WekaExperimentObservation>>>> groupObservations(
            final List<WekaExperimentObservation> observations
    ) {
        final Map<WekaConfigurationKey, Map<String, Map<Integer, List<WekaExperimentObservation>>>> grouped =
                new LinkedHashMap<>();

        for (WekaExperimentObservation observation : observations) {
            if (observation.run() <= 0) {
                throw new IllegalStateException("Encountered non-positive run index: " + observation.run());
            }
            if (observation.fold() <= 0) {
                throw new IllegalStateException("Encountered non-positive fold index: " + observation.fold());
            }

            final WekaConfigurationKey configurationKey = configurationInterpreter.interpret(observation);
            final String schemeSignature = buildSchemeSignature(observation);

            grouped.computeIfAbsent(configurationKey, unused -> new LinkedHashMap<>())
                    .computeIfAbsent(schemeSignature, unused -> new TreeMap<>())
                    .computeIfAbsent(observation.run(), unused -> new ArrayList<>())
                    .add(observation);
        }

        return grouped;
    }

    private String buildSchemeSignature(final WekaExperimentObservation observation) {
        return observation.scheme() + " || " + observation.schemeOptions();
    }

    private List<SchemeAverages> computeSchemeAverages(
            final WekaConfigurationKey configurationKey,
            final Map<String, Map<Integer, List<WekaExperimentObservation>>> observationsByScheme
    ) {
        final List<SchemeAverages> schemeAverages = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, List<WekaExperimentObservation>>> schemeEntry : observationsByScheme.entrySet()) {
            final List<RunAverages> runAverages = computeRunAverages(configurationKey, schemeEntry.getValue());
            schemeAverages.add(new SchemeAverages(
                    averageRunPrecision(runAverages),
                    averageRunRecall(runAverages),
                    averageRunAuc(runAverages),
                    averageRunKappa(runAverages)
            ));
        }

        if (schemeAverages.isEmpty()) {
            throw new IllegalStateException("No scheme variants found for configuration: " + configurationKey);
        }

        if (schemeAverages.size() > 1) {
            System.out.println(
                    "Collapsed " + schemeAverages.size()
                            + " Weka scheme variants into one visible configuration: "
                            + configurationKey
            );
        }

        return List.copyOf(schemeAverages);
    }

    private List<RunAverages> computeRunAverages(
            final WekaConfigurationKey configurationKey,
            final Map<Integer, List<WekaExperimentObservation>> observationsByRun
    ) {
        final List<RunAverages> runAverages = new ArrayList<>();
        Integer expectedFoldCount = null;

        for (Map.Entry<Integer, List<WekaExperimentObservation>> runEntry : observationsByRun.entrySet()) {
            final int runNumber = runEntry.getKey();
            final List<WekaExperimentObservation> runObservations = runEntry.getValue();

            final Set<Integer> observedFolds = new LinkedHashSet<>();
            for (WekaExperimentObservation observation : runObservations) {
                if (!observedFolds.add(observation.fold())) {
                    throw new IllegalStateException(
                            "Duplicate fold " + observation.fold()
                                    + " detected for run " + runNumber
                                    + " and configuration " + configurationKey
                    );
                }
            }

            final int foldCount = observedFolds.size();
            if (expectedFoldCount == null) {
                expectedFoldCount = foldCount;
            } else if (!expectedFoldCount.equals(foldCount)) {
                throw new IllegalStateException(
                        "Inconsistent number of folds for configuration " + configurationKey
                                + ": expected " + expectedFoldCount
                                + " but found " + foldCount
                                + " in run " + runNumber
                );
            }

            runAverages.add(new RunAverages(
                    averagePrecision(runObservations),
                    averageRecall(runObservations),
                    averageAuc(runObservations),
                    averageKappa(runObservations)
            ));
        }

        if (runAverages.isEmpty()) {
            throw new IllegalStateException("No runs found for configuration: " + configurationKey);
        }

        return List.copyOf(runAverages);
    }

    private FinalWekaResultRecord buildFinalRecord(
            final WekaConfigurationKey configurationKey,
            final List<SchemeAverages> schemeAverages
    ) {
        return new FinalWekaResultRecord(
                configurationKey.dataset(),
                configurationKey.classifier(),
                configurationKey.fs(),
                configurationKey.balancing(),
                averageSchemePrecision(schemeAverages),
                averageSchemeRecall(schemeAverages),
                averageSchemeAuc(schemeAverages),
                averageSchemeKappa(schemeAverages),
                ""
        );
    }

    private double averagePrecision(final List<WekaExperimentObservation> observations) {
        double sum = 0.0d;
        for (WekaExperimentObservation observation : observations) {
            sum += observation.precision();
        }
        return sum / observations.size();
    }

    private double averageRecall(final List<WekaExperimentObservation> observations) {
        double sum = 0.0d;
        for (WekaExperimentObservation observation : observations) {
            sum += observation.recall();
        }
        return sum / observations.size();
    }

    private double averageAuc(final List<WekaExperimentObservation> observations) {
        double sum = 0.0d;
        for (WekaExperimentObservation observation : observations) {
            sum += observation.auc();
        }
        return sum / observations.size();
    }

    private double averageKappa(final List<WekaExperimentObservation> observations) {
        double sum = 0.0d;
        for (WekaExperimentObservation observation : observations) {
            sum += observation.kappa();
        }
        return sum / observations.size();
    }

    private double averageRunPrecision(final List<RunAverages> runAverages) {
        double sum = 0.0d;
        for (RunAverages runAverage : runAverages) {
            sum += runAverage.precision();
        }
        return sum / runAverages.size();
    }

    private double averageRunRecall(final List<RunAverages> runAverages) {
        double sum = 0.0d;
        for (RunAverages runAverage : runAverages) {
            sum += runAverage.recall();
        }
        return sum / runAverages.size();
    }

    private double averageRunAuc(final List<RunAverages> runAverages) {
        double sum = 0.0d;
        for (RunAverages runAverage : runAverages) {
            sum += runAverage.auc();
        }
        return sum / runAverages.size();
    }

    private double averageRunKappa(final List<RunAverages> runAverages) {
        double sum = 0.0d;
        for (RunAverages runAverage : runAverages) {
            sum += runAverage.kappa();
        }
        return sum / runAverages.size();
    }

    private double averageSchemePrecision(final List<SchemeAverages> schemeAverages) {
        double sum = 0.0d;
        for (SchemeAverages schemeAverage : schemeAverages) {
            sum += schemeAverage.precision();
        }
        return sum / schemeAverages.size();
    }

    private double averageSchemeRecall(final List<SchemeAverages> schemeAverages) {
        double sum = 0.0d;
        for (SchemeAverages schemeAverage : schemeAverages) {
            sum += schemeAverage.recall();
        }
        return sum / schemeAverages.size();
    }

    private double averageSchemeAuc(final List<SchemeAverages> schemeAverages) {
        double sum = 0.0d;
        for (SchemeAverages schemeAverage : schemeAverages) {
            sum += schemeAverage.auc();
        }
        return sum / schemeAverages.size();
    }

    private double averageSchemeKappa(final List<SchemeAverages> schemeAverages) {
        double sum = 0.0d;
        for (SchemeAverages schemeAverage : schemeAverages) {
            sum += schemeAverage.kappa();
        }
        return sum / schemeAverages.size();
    }

    private Comparator<FinalWekaResultRecord> finalRecordComparator() {
        return Comparator
                .comparingInt((FinalWekaResultRecord record) -> datasetOrder(record.dataset()))
                .thenComparing(FinalWekaResultRecord::dataset)
                .thenComparingInt(record -> classifierOrder(record.classifier()))
                .thenComparing(FinalWekaResultRecord::classifier)
                .thenComparingInt(record -> fsOrder(record.fs()))
                .thenComparing(FinalWekaResultRecord::fs)
                .thenComparingInt(record -> balancingOrder(record.balancing()))
                .thenComparing(FinalWekaResultRecord::balancing);
    }

    private int datasetOrder(final String dataset) {
        return switch (dataset) {
            case "Avro" -> 0;
            case "AvroShuffled" -> 1;
            default -> 2;
        };
    }

    private int classifierOrder(final String classifier) {
        return switch (classifier) {
            case "NaiveBayes" -> 0;
            case "IBk" -> 1;
            case "RandomForest" -> 2;
            default -> 3;
        };
    }

    private int fsOrder(final String fs) {
        return switch (fs) {
            case "No" -> 0;
            case "Yes" -> 1;
            default -> 2;
        };
    }

    private int balancingOrder(final String balancing) {
        return switch (balancing) {
            case "No" -> 0;
            case "Resample" -> 1;
            default -> 2;
        };
    }

    private record RunAverages(
            double precision,
            double recall,
            double auc,
            double kappa
    ) {
    }

    private record SchemeAverages(
            double precision,
            double recall,
            double auc,
            double kappa
    ) {
    }
}
