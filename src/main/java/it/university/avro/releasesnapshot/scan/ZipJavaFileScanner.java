package it.university.avro.releasesnapshot.scan;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipJavaFileScanner {

    private static final String JAVA_EXTENSION = ".java";
    private static final char UNIX_SEPARATOR = '/';

    public List<JavaSourceUnit> scanJavaFiles(final Path archivePath) {
        final String lowerName = archivePath.getFileName().toString().toLowerCase(Locale.ROOT);

        if (lowerName.endsWith(".zip")) {
            return scanZipArchive(archivePath);
        }

        if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")) {
            return scanTarGzArchive(archivePath);
        }

        throw new IllegalStateException("Unsupported archive format: " + archivePath);
    }

    private List<JavaSourceUnit> scanZipArchive(final Path archivePath) {
        final List<JavaSourceUnit> javaSources = new ArrayList<>();

        try (InputStream inputStream = java.nio.file.Files.newInputStream(archivePath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                addJavaSourceFromZipEntry(entry, zipInputStream, javaSources);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan Java files in zip archive " + archivePath, exception);
        }

        return List.copyOf(javaSources);
    }

    private void addJavaSourceFromZipEntry(
            final ZipEntry entry,
            final ZipInputStream zipInputStream,
            final List<JavaSourceUnit> javaSources
    ) throws IOException {
        if (isReadableJavaEntry(entry.getName(), entry.isDirectory())) {
            final String normalizedEntryPath = normalizeArchiveEntryPath(entry.getName());
            final String sourceCode = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
            javaSources.add(new JavaSourceUnit(normalizedEntryPath, sourceCode));
        }
    }

    private List<JavaSourceUnit> scanTarGzArchive(final Path archivePath) {
        final List<JavaSourceUnit> javaSources = new ArrayList<>();

        try (InputStream fileInputStream = java.nio.file.Files.newInputStream(archivePath);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(bufferedInputStream);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {

            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                addJavaSourceFromTarEntry(entry, tarInputStream, javaSources);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan Java files in tar.gz archive " + archivePath, exception);
        }

        return List.copyOf(javaSources);
    }

    private void addJavaSourceFromTarEntry(
            final TarArchiveEntry entry,
            final TarArchiveInputStream tarInputStream,
            final List<JavaSourceUnit> javaSources
    ) throws IOException {
        if (isReadableJavaEntry(entry.getName(), entry.isDirectory())) {
            final String normalizedEntryPath = normalizeArchiveEntryPath(entry.getName());
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            tarInputStream.transferTo(byteArrayOutputStream);
            final String sourceCode = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
            javaSources.add(new JavaSourceUnit(normalizedEntryPath, sourceCode));
        }
    }

    private boolean isReadableJavaEntry(final String entryName, final boolean directory) {
        return !directory && isSafeArchiveEntry(entryName) && entryName.endsWith(JAVA_EXTENSION);
    }

    private boolean isSafeArchiveEntry(final String entryName) {
        final String normalized = entryName.replace('\\', UNIX_SEPARATOR);
        final Path normalizedPath = Path.of(normalized).normalize();
        return !normalizedPath.isAbsolute()
                && !normalizedPath.startsWith("..")
                && normalized.indexOf('\0') < 0;
    }

    private String normalizeArchiveEntryPath(final String rawArchivePath) {
        final String unixStylePath = rawArchivePath.replace('\\', UNIX_SEPARATOR);
        final int firstSlash = unixStylePath.indexOf(UNIX_SEPARATOR);

        if (firstSlash < 0 || firstSlash == unixStylePath.length() - 1) {
            return unixStylePath;
        }

        return unixStylePath.substring(firstSlash + 1);
    }
}
