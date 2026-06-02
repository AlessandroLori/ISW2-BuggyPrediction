package it.university.avro.refactoringdataset.domain;

import java.nio.file.Path;
import java.util.Objects;

public record LocalJavaClass(
        Path sourcePath,
        Path gitRoot,
        String repositoryRelativePath,
        String historyRepositoryRelativePath,
        String classPath,
        String sourceCode
) {
    public LocalJavaClass {
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        gitRoot = Objects.requireNonNull(gitRoot, "gitRoot must not be null");
        repositoryRelativePath = Objects.requireNonNull(repositoryRelativePath, "repositoryRelativePath must not be null");
        historyRepositoryRelativePath = Objects.requireNonNull(
                historyRepositoryRelativePath,
                "historyRepositoryRelativePath must not be null"
        );
        classPath = Objects.requireNonNull(classPath, "classPath must not be null");
        sourceCode = Objects.requireNonNull(sourceCode, "sourceCode must not be null");
    }
}
