package it.university.avro.datasetprep;

import it.university.avro.dataset.service.TrainingCsvCutService;
import it.university.avro.datasetprep.config.DatasetPreparationConfiguration;
import it.university.avro.datasetprep.csv.TabularCsvReader;
import it.university.avro.datasetprep.csv.TabularCsvWriter;
import it.university.avro.datasetprep.service.StratifiedStandardizedDatasetService;

import java.nio.file.Path;

public final class DatasetPreparationApplication {

    private DatasetPreparationApplication() {
    }

    public static void main(final String[] args) throws Exception {
        final DatasetPreparationConfiguration configuration =
                DatasetPreparationConfiguration.defaultConfiguration();

        final StratifiedStandardizedDatasetService service =
                new StratifiedStandardizedDatasetService(
                        new TabularCsvReader(),
                        new TabularCsvWriter(),
                        configuration
                );

        service.prepare();

        final TrainingCsvCutService trainingCsvCutService = new TrainingCsvCutService();
        final Path cuttedTrainingCsvPath = Path.of("output", "ReleaseMetricsCutted_train.csv");

        trainingCsvCutService.generateCuttedTrainingCsv(
                configuration.trainingOutputCsvPath(),
                cuttedTrainingCsvPath
        );

        System.out.println("Generated cutted training csv: " + cuttedTrainingCsvPath);
    }
}