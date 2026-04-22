package it.university.avro.smellspmd.pmd;

import it.university.avro.smellspmd.domain.PmdClassSmellMetrics;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PmdJavaSmellAnalyzer {

    private static final String JAVA_LANGUAGE_ID = "java";
    private static final String JAVA_LANGUAGE_VERSION = "17";

    public Map<String, PmdClassSmellMetrics> analyzeByClassPath(
            final Map<String, String> sourceByResolvedClassPath,
            final String rulesetPath
    ) {
        if (sourceByResolvedClassPath.isEmpty()) {
            return Map.of();
        }

        final PMDConfiguration configuration = new PMDConfiguration();
        configuration.setThreads(1);
        configuration.setDefaultLanguageVersion(resolveJavaLanguageVersion());
        configuration.addRuleSet(rulesetPath);

        try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
            for (Map.Entry<String, String> entry : sourceByResolvedClassPath.entrySet()) {
                analysis.files().addSourceFile(
                        FileId.fromPathLikeString(entry.getKey()),
                        entry.getValue()
                );
            }

            final Report report = analysis.performAnalysisAndCollectReport();
            final Map<String, Integer> smellCounts = initializeCountMap(sourceByResolvedClassPath);
            final Map<String, Set<String>> distinctRuleNames = initializeDistinctRuleMap(sourceByResolvedClassPath);

            report.getProcessingErrors().forEach(error -> System.out.println(
                    "[PMD-PROCESSING-ERROR] file=" + error.getFileId().getOriginalPath()
                            + " | message=" + error.getMsg()
            ));

            report.getConfigurationErrors().forEach(error -> System.out.println(
                    "[PMD-CONFIG-ERROR] rule=" + error.rule().getName()
                            + " | message=" + error.issue()
            ));

            for (RuleViolation violation : report.getViolations()) {
                final String classPath = normalizePath(violation.getFileId().getOriginalPath());
                smellCounts.merge(classPath, 1, Integer::sum);
                distinctRuleNames
                        .computeIfAbsent(classPath, ignored -> new LinkedHashSet<>())
                        .add(violation.getRule().getName());
            }

            final Map<String, PmdClassSmellMetrics> result = new LinkedHashMap<>();
            for (String classPath : smellCounts.keySet()) {
                result.put(
                        classPath,
                        new PmdClassSmellMetrics(
                                smellCounts.getOrDefault(classPath, 0),
                                distinctRuleNames.getOrDefault(classPath, Set.of()).size()
                        )
                );
            }

            return Map.copyOf(result);
        }
    }

    private LanguageVersion resolveJavaLanguageVersion() {
        final LanguageVersion explicitVersion =
                LanguageRegistry.PMD.getLanguageVersionById(JAVA_LANGUAGE_ID, JAVA_LANGUAGE_VERSION);

        if (explicitVersion != null) {
            return explicitVersion;
        }

        final Language javaLanguage = LanguageRegistry.PMD.getLanguageById(JAVA_LANGUAGE_ID);
        if (javaLanguage == null) {
            throw new IllegalStateException(
                    "PMD Java language module not found. Verify dependency net.sourceforge.pmd:pmd-java."
            );
        }

        return javaLanguage.getDefaultVersion();
    }

    private Map<String, Integer> initializeCountMap(final Map<String, String> sourceByResolvedClassPath) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        for (String classPath : sourceByResolvedClassPath.keySet()) {
            result.put(normalizePath(classPath), 0);
        }
        return result;
    }

    private Map<String, Set<String>> initializeDistinctRuleMap(final Map<String, String> sourceByResolvedClassPath) {
        final Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String classPath : sourceByResolvedClassPath.keySet()) {
            result.put(normalizePath(classPath), new LinkedHashSet<>());
        }
        return result;
    }

    private String normalizePath(final String path) {
        return path.replace('\\', '/');
    }
}
