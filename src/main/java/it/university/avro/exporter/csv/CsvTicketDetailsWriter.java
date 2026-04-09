package it.university.avro.exporter.csv;

import it.university.avro.exporter.domain.TicketDetailsRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class CsvTicketDetailsWriter implements TicketDetailsWriter {

    private final Path outputPath;

    public CsvTicketDetailsWriter(final Path outputPath) {
        this.outputPath = Objects.requireNonNull(outputPath, "outputPath must not be null");
    }

    @Override
    public void write(final List<TicketDetailsRecord> records) {
        Objects.requireNonNull(records, "records must not be null");

        try {
            createParentDirectoryIfNeeded();
            try (Writer writer = Files.newBufferedWriter(outputPath);
                 CSVPrinter csvPrinter = new CSVPrinter(writer, buildCsvFormat())) {
                for (TicketDetailsRecord record : records) {
                    csvPrinter.printRecord(
                            record.ticketId(),
                            record.createdDate(),
                            record.closedDate(),
                            record.openingVersion(),
                            record.openingVersionDate(),
                            record.affectedVersions(),
                            record.affectedVersionCount(),
                            record.fixedVersions(),
                            record.fixedVersionDate()
                    );
                }
            }
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to write CSV file to " + outputPath, exception);
        }
    }

    private CSVFormat buildCsvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader(
                        "ticket_id",
                        "created_date",
                        "closed_date",
                        "opening_version",
                        "opening_version_date",
                        "affected_versions",
                        "affected_version_count",
                        "fixed_versions",
                        "fixed_version_date"
                )
                .build();
    }

    private void createParentDirectoryIfNeeded() throws IOException {
        final Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
