package it.university.avro.releasesnapshot.scan;

import java.util.Locale;
import java.util.Objects;

public final class ProductionJavaClassFilter {

    public boolean isEligible(final String archivePath, final String className) {
        Objects.requireNonNull(archivePath, "archivePath must not be null");
        Objects.requireNonNull(className, "className must not be null");

        if (isInTestDirectory(archivePath)) {
            return false;
        }

        if ("package-info".equals(className)) {
            return false;
        }

        if ("module-info".equals(className)) {
            return false;
        }

        return !className.startsWith("Test");
    }

    private boolean isInTestDirectory(final String archivePath) {
        final String normalizedPath = archivePath.replace('\\', '/');
        final String[] segments = normalizedPath.split("/");

        for (int index = 0; index < segments.length - 1; index++) {
            final String segment = segments[index].toLowerCase(Locale.ROOT);

            if (segment.contains("test")) {
                return true;
            }
        }

        return false;
    }
}