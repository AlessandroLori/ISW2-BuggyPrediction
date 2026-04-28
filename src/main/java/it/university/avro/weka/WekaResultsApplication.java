package it.university.avro.weka;

import it.university.avro.weka.config.WekaResultsConfiguration;
import it.university.avro.weka.csv.FinalWekaResultsCsvWriter;
import it.university.avro.weka.csv.WekaExperimentObservationCsvReader;
import it.university.avro.weka.service.WekaResultsAggregationService;
import it.university.avro.weka.util.WekaConfigurationInterpreter;

public final class WekaResultsApplication {

    private WekaResultsApplication() {
    }

    public static void main(final String[] args) {
        final WekaResultsConfiguration configuration = WekaResultsConfiguration.defaultConfiguration();

        final WekaResultsAggregationService service = new WekaResultsAggregationService(
                new WekaExperimentObservationCsvReader(),
                new FinalWekaResultsCsvWriter(configuration.outputCsvPath()),
                new WekaConfigurationInterpreter()
        );

        service.generate(configuration.inputCsvPath());
    }
}
