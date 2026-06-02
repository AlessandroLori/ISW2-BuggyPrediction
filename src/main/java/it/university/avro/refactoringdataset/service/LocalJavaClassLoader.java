package it.university.avro.refactoringdataset.service;

import it.university.avro.refactoringdataset.domain.LocalJavaClass;
import it.university.avro.refactoringdataset.git.LocalGitRepositoryResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalJavaClassLoader {

    private static final String PRODUCTION_SOURCE_MARKER = "src/main/java/";

    private final LocalGitRepositoryResolver gitRepositoryResolver;

    public LocalJavaClassLoader() {
        this.gitRepositoryResolver = new LocalGitRepositoryResolver();
    }

    public LocalJavaClass load(Path sourcePath) {
        Path normalizedSourcePath = sourcePath.toAbsolutePath().normalize();
        validateSourcePath(normalizedSourcePath);

        Path gitRoot = resolveGitRoot(normalizedSourcePath);
        String repositoryRelativePath = repositoryRelativePath(gitRoot, normalizedSourcePath);
        String classPath = resolveOutputClassPath(repositoryRelativePath);
        String sourceCode = readSourceCode(normalizedSourcePath);

        return new LocalJavaClass(
                normalizedSourcePath,
                gitRoot,
                repositoryRelativePath,
                repositoryRelativePath,
                classPath,
                sourceCode
        );
    }

    public LocalJavaClass loadRefactoringVariant(Path variantSourcePath, Path historySourcePath) {
        Path normalizedVariantPath = variantSourcePath.toAbsolutePath().normalize();
        Path normalizedHistoryPath = historySourcePath.toAbsolutePath().normalize();
        validateSourcePath(normalizedVariantPath);
        validateSourcePath(normalizedHistoryPath);

        Path gitRoot = resolveGitRoot(normalizedVariantPath);
        String variantRelativePath = repositoryRelativePath(gitRoot, normalizedVariantPath);
        String historyRelativePath = repositoryRelativePath(gitRoot, normalizedHistoryPath);
        String sourceCode = readSourceCode(normalizedVariantPath);

        return new LocalJavaClass(
                normalizedVariantPath,
                gitRoot,
                variantRelativePath,
                historyRelativePath,
                resolveOutputClassPath(variantRelativePath),
                sourceCode
        );
    }

    private Path resolveGitRoot(Path sourcePath) {
        return gitRepositoryResolver.resolveGitRoot(sourcePath)
                .orElseThrow(() -> new IllegalStateException("No Git repository root found for: " + sourcePath));
    }

    private String repositoryRelativePath(Path gitRoot, Path sourcePath) {
        return normalizePath(gitRoot.relativize(sourcePath).toString());
    }

    private void validateSourcePath(Path sourcePath) {
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source file not found: " + sourcePath);
        }
        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("Source path is not a file: " + sourcePath);
        }
        if (!sourcePath.toString().endsWith(".java")) {
            throw new IllegalArgumentException("Source path is not a Java file: " + sourcePath);
        }
        if (normalizePath(sourcePath.toString()).contains("/src/test/")) {
            throw new IllegalArgumentException("Test source files are not allowed: " + sourcePath);
        }
    }

    private String resolveOutputClassPath(String repositoryRelativePath) {
        int markerIndex = repositoryRelativePath.indexOf(PRODUCTION_SOURCE_MARKER);
        if (markerIndex < 0) {
            return repositoryRelativePath;
        }

        return repositoryRelativePath.substring(markerIndex + PRODUCTION_SOURCE_MARKER.length());
    }

    private String readSourceCode(Path sourcePath) {
        try {
            return Files.readString(sourcePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read source file: " + sourcePath, exception);
        }
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/');
    }
}
