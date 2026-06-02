package it.university.avro.releasesnapshot.scan;

import java.util.Objects;

public final class LogicalClassPathResolver {

    private static final String JAVA_EXTENSION = ".java";
    private static final String PATH_SEPARATOR = "/";

    public String resolve(
            final String archivePath,
            final String packageName,
            final String typeName
    ) {
        Objects.requireNonNull(archivePath, "archivePath must not be null");
        Objects.requireNonNull(typeName, "typeName must not be null");

        final String normalizedArchivePath = archivePath.replace('\\', PATH_SEPARATOR.charAt(0));
        final SourceRootLocation sourceRootLocation = detectSourceRootLocation(normalizedArchivePath);

        final String normalizedPackageName = packageName == null ? "" : packageName.trim();
        final String packagePath = normalizedPackageName.isBlank()
                ? ""
                : normalizedPackageName.replace('.', PATH_SEPARATOR.charAt(0)) + PATH_SEPARATOR;

        if (sourceRootLocation == null) {
            return packagePath.isBlank()
                    ? typeName + JAVA_EXTENSION
                    : packagePath + typeName + JAVA_EXTENSION;
        }

        return sourceRootLocation.prefixBeforeSourceRoot()
                + sourceRootLocation.sourceRoot()
                + packagePath
                + typeName
                + JAVA_EXTENSION;
    }

    private SourceRootLocation detectSourceRootLocation(final String normalizedArchivePath) {
        final String[] supportedRoots = {
                "src/main/java" + PATH_SEPARATOR,
                "src/java" + PATH_SEPARATOR
        };

        for (String sourceRoot : supportedRoots) {
            final int index = normalizedArchivePath.indexOf(sourceRoot);
            if (index >= 0) {
                final String prefix = normalizedArchivePath.substring(0, index);
                return new SourceRootLocation(prefix, sourceRoot);
            }
        }

        return null;
    }

    private record SourceRootLocation(
            String prefixBeforeSourceRoot,
            String sourceRoot
    ) {
    }
}