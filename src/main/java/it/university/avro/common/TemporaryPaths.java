package it.university.avro.common;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class TemporaryPaths {

    private static final String TEMP_ROOT_DIRECTORY = ".tmp";
    private static final int MAX_CREATION_ATTEMPTS = 100;
    private static final AtomicLong COUNTER = new AtomicLong();

    private TemporaryPaths() {
    }

    public static Path createDirectory(final String prefix) throws IOException {
        for (int attempt = 0; attempt < MAX_CREATION_ATTEMPTS; attempt++) {
            final Path candidate = uniquePath(prefix, "");
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Try another deterministic candidate within the project-private temporary root.
            }
        }
        throw new IOException("Unable to create temporary directory after " + MAX_CREATION_ATTEMPTS + " attempts");
    }

    public static Path createFile(final String prefix, final String suffix) throws IOException {
        for (int attempt = 0; attempt < MAX_CREATION_ATTEMPTS; attempt++) {
            final Path candidate = uniquePath(prefix, suffix);
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Try another deterministic candidate within the project-private temporary root.
            }
        }
        throw new IOException("Unable to create temporary file after " + MAX_CREATION_ATTEMPTS + " attempts");
    }

    private static Path uniquePath(final String prefix, final String suffix) throws IOException {
        final String safePrefix = sanitize(prefix);
        final String safeSuffix = sanitizeSuffix(suffix);
        final String uniqueName = safePrefix
                + System.nanoTime()
                + "-"
                + COUNTER.incrementAndGet()
                + safeSuffix;
        return ensureProjectTempRoot().resolve(uniqueName);
    }

    private static Path ensureProjectTempRoot() throws IOException {
        final Path tempRoot = Path.of(TEMP_ROOT_DIRECTORY).toAbsolutePath().normalize();
        Files.createDirectories(tempRoot);
        return tempRoot;
    }

    private static String sanitize(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "tmp-";
        }
        return rawValue.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static String sanitizeSuffix(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }
        return sanitize(rawValue);
    }
}
