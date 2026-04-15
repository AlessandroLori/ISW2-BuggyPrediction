package it.university.avro.releasesnapshot.scan;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipJavaFileScanner {

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

        try (InputStream inputStream = Files.newInputStream(archivePath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                final String normalizedEntryPath = normalizeArchiveEntryPath(entry.getName());

                if (!normalizedEntryPath.endsWith(".java")) {
                    continue;
                }

                final String sourceCode = new String(
                        zipInputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );

                javaSources.add(new JavaSourceUnit(normalizedEntryPath, sourceCode));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan Java files in zip archive " + archivePath, exception);
        }

        return List.copyOf(javaSources);
    }

    private List<JavaSourceUnit> scanTarGzArchive(final Path archivePath) {
        final List<JavaSourceUnit> javaSources = new ArrayList<>();

        try (InputStream fileInputStream = Files.newInputStream(archivePath);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(bufferedInputStream);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {

            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                final String normalizedEntryPath = normalizeArchiveEntryPath(entry.getName());

                if (!normalizedEntryPath.endsWith(".java")) {
                    continue;
                }

                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                tarInputStream.transferTo(byteArrayOutputStream);

                final String sourceCode = byteArrayOutputStream.toString(StandardCharsets.UTF_8);

                javaSources.add(new JavaSourceUnit(normalizedEntryPath, sourceCode));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan Java files in tar.gz archive " + archivePath, exception);
        }

        return List.copyOf(javaSources);
    }

    private String normalizeArchiveEntryPath(final String rawArchivePath) {
        final String unixStylePath = rawArchivePath.replace('\\', '/');
        final int firstSlash = unixStylePath.indexOf('/');

        if (firstSlash < 0 || firstSlash == unixStylePath.length() - 1) {
            return unixStylePath;
        }

        return unixStylePath.substring(firstSlash + 1);
    }
}