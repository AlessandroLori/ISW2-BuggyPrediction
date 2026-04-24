package it.university.avro.weka.io;

import it.university.avro.weka.domain.FeatureSelectionReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FeatureSelectionReportWriter {

    public void writeAll(final Path outputDirectory, final List<FeatureSelectionReport> reports) throws IOException {
        Files.createDirectories(outputDirectory);
        for (FeatureSelectionReport report : reports) {
            final Path outputPath = outputDirectory.resolve(report.fileName());
            Files.writeString(outputPath, report.content(), StandardCharsets.UTF_8);
        }
    }
}
