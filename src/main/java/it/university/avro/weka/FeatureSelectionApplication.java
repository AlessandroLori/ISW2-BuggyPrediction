package it.university.avro.weka;

import it.university.avro.weka.config.WekaFeatureSelectionConfiguration;
import it.university.avro.weka.io.FeatureSelectionReportWriter;
import it.university.avro.weka.io.TrainingInstancesLoader;
import it.university.avro.weka.service.WekaFeatureSelectionService;

public final class FeatureSelectionApplication {

    private FeatureSelectionApplication() {
    }

    public static void main(final String[] args) throws Exception {
        final WekaFeatureSelectionConfiguration configuration =
                WekaFeatureSelectionConfiguration.defaultConfiguration();

        final WekaFeatureSelectionService service = new WekaFeatureSelectionService(
                new TrainingInstancesLoader(),
                new FeatureSelectionReportWriter(),
                configuration
        );

        service.run();
    }
}
