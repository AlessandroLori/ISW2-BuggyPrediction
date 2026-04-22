package it.university.avro.datasetprep;

import it.university.avro.datasetprep.config.DatasetPreparationConfiguration;
import it.university.avro.datasetprep.csv.TabularCsvReader;
import it.university.avro.datasetprep.csv.TabularCsvWriter;
import it.university.avro.datasetprep.service.StratifiedLogNormalizedDatasetService;

public final class DatasetPreparationApplication {

    private DatasetPreparationApplication() {
    }

    public static void main(final String[] args) {
        final DatasetPreparationConfiguration configuration =
                DatasetPreparationConfiguration.defaultConfiguration();

        final StratifiedLogNormalizedDatasetService service =
                new StratifiedLogNormalizedDatasetService(
                        new TabularCsvReader(),
                        new TabularCsvWriter(),
                        configuration
                );

        service.prepare();
    }
}