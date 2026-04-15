package it.university.avro.releasesnapshot.csv;

import it.university.avro.releasesnapshot.domain.JavaClassRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ReleaseClassInventoryCsvWriter {

    private final Path outputCsvPath;

    public ReleaseClassInventoryCsvWriter(final Path outputCsvPath) {
        this.outputCsvPath = outputCsvPath;
    }

    public void write(final List<JavaClassRecord> records) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputCsvPath)) {
            writer.write("version,classpath,features,nsmells,BUGGY");
            writer.newLine();

            for (JavaClassRecord record : records) {
                writer.write(csv(record.version()));
                writer.write(",");
                writer.write(csv(record.classPath()));
                writer.write(",");
                writer.write(csv(record.features()));
                writer.write(",");
                writer.write(csv(record.nsmells()));
                writer.write(",");
                writer.write(csv(record.buggy()));
                writer.newLine();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to write ReleaseClassInventory CSV to " + outputCsvPath,
                    exception
            );
        }
    }

    private String csv(final String value) {
        final String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}