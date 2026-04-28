package it.university.avro.weka.util;

import it.university.avro.weka.domain.WekaConfigurationKey;
import it.university.avro.weka.domain.WekaExperimentObservation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WekaConfigurationInterpreter {

    private static final Pattern CLASSIFIER_PATTERN = Pattern.compile(
            "weka\\.classifiers\\.(?:bayes\\.NaiveBayes|lazy\\.IBk|trees\\.RandomForest)"
    );

    public WekaConfigurationKey interpret(final WekaExperimentObservation observation) {
        final String combinedSchemeDescription = observation.scheme() + " " + observation.schemeOptions();

        return new WekaConfigurationKey(
                resolveDatasetName(observation.datasetKey()),
                resolveClassifierName(combinedSchemeDescription),
                containsAttributeSelection(combinedSchemeDescription) ? "Yes" : "No",
                containsBalancing(combinedSchemeDescription) ? "Resample" : "No"
        );
    }

    private String resolveDatasetName(final String datasetKey) {
        final String normalized = datasetKey.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("dataset")) {
            return "Avro";
        }
        if (normalized.equals("dataset_shuffled") || normalized.equals("datasetshuffled")) {
            return "AvroShuffled";
        }
        return datasetKey.trim();
    }

    private String resolveClassifierName(final String combinedSchemeDescription) {
        final Matcher matcher = CLASSIFIER_PATTERN.matcher(combinedSchemeDescription);

        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group();
        }

        if (lastMatch == null) {
            throw new IllegalStateException(
                    "Unable to resolve classifier from Weka scheme: " + combinedSchemeDescription
            );
        }

        return switch (lastMatch) {
            case "weka.classifiers.bayes.NaiveBayes" -> "NaiveBayes";
            case "weka.classifiers.lazy.IBk" -> "IBk";
            case "weka.classifiers.trees.RandomForest" -> "RandomForest";
            default -> throw new IllegalStateException("Unsupported classifier: " + lastMatch);
        };
    }

    private boolean containsAttributeSelection(final String combinedSchemeDescription) {
        return combinedSchemeDescription.contains("weka.filters.supervised.attribute.AttributeSelection")
                || combinedSchemeDescription.contains("weka.attributeSelection.WrapperSubsetEval");
    }

    private boolean containsBalancing(final String combinedSchemeDescription) {
        return combinedSchemeDescription.contains("weka.filters.supervised.instance.Resample");
    }
}
