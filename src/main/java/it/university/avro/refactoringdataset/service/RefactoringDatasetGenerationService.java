package it.university.avro.refactoringdataset.service;

import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.domain.StaticMetrics;
import it.university.avro.metrics.snapshot.JavaLineMetricExtractor;
import it.university.avro.refactoringdataset.config.RefactoringDatasetConfiguration;
import it.university.avro.refactoringdataset.csv.RefactoringMetricsCsvWriter;
import it.university.avro.refactoringdataset.domain.LocalJavaClass;
import it.university.avro.refactoringdataset.domain.RefactoringMetricsRecord;
import it.university.avro.refactoringdataset.git.LocalGitHistoryMetricExtractor;
import it.university.avro.smellspmd.domain.PmdClassSmellMetrics;
import it.university.avro.smellspmd.pmd.PmdJavaSmellAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RefactoringDatasetGenerationService {

    private final JavaLineMetricExtractor staticMetricExtractor;
    private final LocalGitHistoryMetricExtractor historyMetricExtractor;
    private final PmdJavaSmellAnalyzer pmdJavaSmellAnalyzer;
    private final RefactoringMetricsCsvWriter csvWriter;
    private final LocalJavaClassLoader classLoader;

    public RefactoringDatasetGenerationService(
            JavaLineMetricExtractor staticMetricExtractor,
            LocalGitHistoryMetricExtractor historyMetricExtractor,
            PmdJavaSmellAnalyzer pmdJavaSmellAnalyzer,
            RefactoringMetricsCsvWriter csvWriter
    ) {
        this.staticMetricExtractor = staticMetricExtractor;
        this.historyMetricExtractor = historyMetricExtractor;
        this.pmdJavaSmellAnalyzer = pmdJavaSmellAnalyzer;
        this.csvWriter = csvWriter;
        this.classLoader = new LocalJavaClassLoader();
    }

    public void generate(RefactoringDatasetConfiguration configuration) {
        logConfiguration(configuration);
        List<LocalJavaClass> classes = loadClasses(configuration);
        Map<String, PmdClassSmellMetrics> pmdMetricsByClassPath = analyzePmdSmells(
                classes,
                configuration.pmdRulesetPath()
        );

        List<RefactoringMetricsRecord> records = new ArrayList<>();
        for (LocalJavaClass javaClass : classes) {
            records.add(buildMetricsRecord(javaClass, pmdMetricsByClassPath));
        }

        csvWriter.write(records);
    }

    private RefactoringMetricsRecord buildMetricsRecord(
            LocalJavaClass javaClass,
            Map<String, PmdClassSmellMetrics> pmdMetricsByClassPath
    ) {
        StaticMetrics staticMetrics = staticMetricExtractor.extract(javaClass.sourceCode());
        HistoryMetrics historyMetrics = historyMetricExtractor.extract(
                javaClass.gitRoot(),
                javaClass.historyRepositoryRelativePath()
        );

        PmdClassSmellMetrics pmdMetrics = pmdMetricsByClassPath.getOrDefault(
                javaClass.classPath(),
                PmdClassSmellMetrics.empty()
        );

        RefactoringMetricsRecord record = RefactoringMetricsRecord.from(
                javaClass.classPath(),
                staticMetrics,
                historyMetrics,
                pmdMetrics.distinctSmellTypes(),
                pmdMetrics.smellCount()
        );

        System.out.println(
                "Generated row for " + javaClass.classPath()
                        + " | source=" + javaClass.repositoryRelativePath()
                        + " | history=" + javaClass.historyRepositoryRelativePath()
                        + " | PMD=" + pmdMetrics.smellCount()
                        + " | SONAR_SMELLS left blank"
        );
        return record;
    }

    private List<LocalJavaClass> loadClasses(RefactoringDatasetConfiguration configuration) {
        List<Path> productionSourcePaths = configuration.productionSourcePaths();
        if (productionSourcePaths == null || productionSourcePaths.isEmpty()) {
            throw new IllegalArgumentException("At least one Java source path is required");
        }

        List<LocalJavaClass> classes = new ArrayList<>();
        for (Path sourcePath : productionSourcePaths) {
            classes.add(classLoader.load(sourcePath));
            classes.addAll(loadRefactoringVariants(sourcePath, configuration));
        }
        return List.copyOf(classes);
    }

    private List<LocalJavaClass> loadRefactoringVariants(
            Path originalSourcePath,
            RefactoringDatasetConfiguration configuration
    ) {
        List<LocalJavaClass> variants = new ArrayList<>();
        String originalSimpleName = stripJavaExtension(originalSourcePath.getFileName().toString());
        Path savesDirectory = configuration.refactoringSavesDirectory().toAbsolutePath().normalize();

        if (!Files.isDirectory(savesDirectory)) {
            System.out.println("[REFACTORING-SAVES-WARNING] saves directory not found: " + savesDirectory);
            return variants;
        }

        for (int variantIndex = 1; variantIndex <= configuration.refactoringVariantCount(); variantIndex++) {
            String expectedFileName = originalSimpleName + "_C" + variantIndex + ".java";
            Path normalizedVariantPath = savesDirectory.resolve(expectedFileName).toAbsolutePath().normalize();

            if (!Files.isRegularFile(normalizedVariantPath)) {
                System.out.println("[REFACTORING-SKIP] missing variant: " + normalizedVariantPath);
                continue;
            }

            System.out.println("[REFACTORING-FOUND] " + originalSimpleName + " C" + variantIndex
                    + " -> " + normalizedVariantPath);
            variants.add(classLoader.loadRefactoringVariant(normalizedVariantPath, originalSourcePath));
        }
        return variants;
    }

    private void logConfiguration(RefactoringDatasetConfiguration configuration) {
        System.out.println("[REFACTORING-CONFIG] outputCsv="
                + configuration.outputCsvPath().toAbsolutePath().normalize());
        System.out.println("[REFACTORING-CONFIG] variantCount=" + configuration.refactoringVariantCount());
        System.out.println("[REFACTORING-CONFIG] savesDir="
                + configuration.refactoringSavesDirectory().toAbsolutePath().normalize());
        System.out.println("[REFACTORING-CONFIG] savesDirExists="
                + Files.isDirectory(configuration.refactoringSavesDirectory().toAbsolutePath().normalize()));
        logSavesDirectoryContents(configuration.refactoringSavesDirectory());
    }

    private void logSavesDirectoryContents(Path savesDirectory) {
        Path normalizedSavesDirectory = savesDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedSavesDirectory)) {
            return;
        }

        try (Stream<Path> files = Files.list(normalizedSavesDirectory)) {
            List<String> javaFileNames = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            System.out.println("[REFACTORING-CONFIG] savesJavaFiles=" + javaFileNames);
        } catch (IOException exception) {
            System.out.println("[REFACTORING-SAVES-WARNING] unable to list saves directory: "
                    + normalizedSavesDirectory + " | " + exception.getMessage());
        }
    }

    private String stripJavaExtension(String fileName) {
        if (!fileName.endsWith(".java")) {
            return fileName;
        }
        return fileName.substring(0, fileName.length() - ".java".length());
    }

    private Map<String, PmdClassSmellMetrics> analyzePmdSmells(
            List<LocalJavaClass> classes,
            String pmdRulesetPath
    ) {
        Map<String, String> sourceByClassPath = new LinkedHashMap<>();
        for (LocalJavaClass javaClass : classes) {
            sourceByClassPath.put(javaClass.classPath(), javaClass.sourceCode());
        }

        return pmdJavaSmellAnalyzer.analyzeByClassPath(sourceByClassPath, pmdRulesetPath);
    }
}
