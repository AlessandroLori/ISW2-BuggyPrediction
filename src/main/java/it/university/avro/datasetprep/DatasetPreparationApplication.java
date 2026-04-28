package it.university.avro.datasetprep;

import it.university.avro.datasetprep.config.DatasetPreparationConfiguration;
import it.university.avro.datasetprep.csv.TabularCsvReader;
import it.university.avro.datasetprep.csv.TabularCsvWriter;
import it.university.avro.datasetprep.service.StratifiedStandardizedDatasetService;

public final class DatasetPreparationApplication {

    private DatasetPreparationApplication() {
    }

    public static void main(final String[] args) {
        final DatasetPreparationConfiguration configuration =
                DatasetPreparationConfiguration.defaultConfiguration();

        final StratifiedStandardizedDatasetService service =
                new StratifiedStandardizedDatasetService(
                        new TabularCsvReader(),
                        new TabularCsvWriter(),
                        configuration
                );

        service.prepare();
    }
}
