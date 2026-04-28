package it.university.avro.weka.domain;

public record WekaExperimentObservation(
        String datasetKey,
        int run,
        int fold,
        String scheme,
        String schemeOptions,
        double precision,
        double recall,
        double auc,
        double kappa
) {
}
