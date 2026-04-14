package it.university.avro.exporter.iv.csv;

import it.university.avro.exporter.iv.domain.TicketWithInjectedVersionRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class TicketDetailsWithIvWriter {

    private final Path outputPath;

    public TicketDetailsWithIvWriter(final Path outputPath) {
        this.outputPath = Objects.requireNonNull(outputPath, "outputPath must not be null");
    }

    public void write(final List<TicketWithInjectedVersionRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");

        try {
            createParentDirectoryIfNeeded();

            try (Writer writer = Files.newBufferedWriter(outputPath);
                 CSVPrinter csvPrinter = new CSVPrinter(writer, buildCsvFormat())) {

                for (TicketWithInjectedVersionRow row : rows) {
                    csvPrinter.printRecord(
                            row.ticketId(),
                            row.createdDate(),
                            row.closedDate(),
                            row.openingVersion(),
                            row.openingVersionDate(),
                            row.affectedVersionCount(),
                            row.affectedVersion(),
                            row.fixedVersion(),
                            row.fixedVersionDate(),
                            row.injectedVersion()
                    );
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write CSV file to " + outputPath, exception);
        }
    }

    private CSVFormat buildCsvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader(
                        "ticket id",
                        "create date",
                        "closed date",
                        "opening version",
                        "opening version date",
                        "affected version count",
                        "affected version",
                        "fixed version",
                        "fixed version date",
                        "injected version"
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