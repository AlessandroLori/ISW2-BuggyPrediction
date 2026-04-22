package it.university.avro.datasetprep.csv;

import it.university.avro.datasetprep.domain.TabularDataset;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TabularCsvWriter {

    public void write(final TabularDataset dataset, final Path outputPath) {
        try {
            final Path parentDirectory = outputPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            try (Writer writer = Files.newBufferedWriter(outputPath);
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

                final List<String> headers = dataset.headers();
                printer.printRecord(headers);

                for (Map<String, String> row : dataset.rows()) {
                    for (String header : headers) {
                        printer.print(row.getOrDefault(header, ""));
                    }
                    printer.println();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write csv: " + outputPath, exception);
        }
    }
}