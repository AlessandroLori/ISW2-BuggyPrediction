package it.university.avro.refactoringdataset;

import it.university.avro.common.ApplicationLog;

import it.university.avro.metrics.snapshot.JavaLineMetricExtractor;
import it.university.avro.refactoringdataset.config.RefactoringDatasetConfiguration;
import it.university.avro.refactoringdataset.csv.RefactoringMetricsCsvWriter;
import it.university.avro.refactoringdataset.git.LocalGitHistoryMetricExtractor;
import it.university.avro.refactoringdataset.service.RefactoringDatasetGenerationService;
import it.university.avro.smellspmd.pmd.PmdJavaSmellAnalyzer;

public final class RefactoringDatasetApplication {

    private RefactoringDatasetApplication() {
        // Utility class.
    }

    public static void main(String[] args) {
        RefactoringDatasetConfiguration configuration = RefactoringDatasetConfiguration.defaultConfiguration();

        RefactoringDatasetGenerationService service = new RefactoringDatasetGenerationService(
                new JavaLineMetricExtractor(),
                new LocalGitHistoryMetricExtractor(),
                new PmdJavaSmellAnalyzer(),
                new RefactoringMetricsCsvWriter(configuration.outputCsvPath())
        );

        service.generate(configuration);
        ApplicationLog.info("Generated refactoring metrics csv: " + configuration.outputCsvPath());
    }
}
