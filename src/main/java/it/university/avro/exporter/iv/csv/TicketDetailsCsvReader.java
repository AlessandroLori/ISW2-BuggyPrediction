package it.university.avro.exporter.iv.csv;

import it.university.avro.exporter.iv.domain.TicketCsvRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TicketDetailsCsvReader {

    private final Path inputPath;

    public TicketDetailsCsvReader(final Path inputPath) {
        this.inputPath = Objects.requireNonNull(inputPath, "inputPath must not be null");
    }

    public List<TicketCsvRow> read() {
        final List<TicketCsvRow> rows = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(inputPath);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                rows.add(new TicketCsvRow(
                        getValue(record, "ticket id", "ticket_id"),
                        getValue(record, "create date", "created_date"),
                        getValue(record, "closed date", "closed_date"),
                        getValue(record, "opening version", "opening_version"),
                        getValue(record, "opening version date", "opening_version_date"),
                        parseAffectedVersionCount(getValue(record, "affected version count", "affected_version_count")),
                        getValue(record, "affected version", "affected_versions", "affected version"),
                        getValue(record, "fixed version", "fixed_versions"),
                        getValue(record, "fixed version date", "fixed_version_date")
                ));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read CSV file from " + inputPath, exception);
        }

        return List.copyOf(rows);
    }

    private String getValue(final CSVRecord record, final String... headerCandidates) {
        for (String header : headerCandidates) {
            if (record.isMapped(header)) {
                return record.get(header);
            }
        }
        throw new IllegalArgumentException("Missing expected CSV header. Candidates: " + String.join(", ", headerCandidates));
    }

    private int parseAffectedVersionCount(final String rawValue) {
        if (rawValue == null || rawValue.isBlank() || "n/a".equalsIgnoreCase(rawValue.trim())) {
            return 0;
        }
        return Integer.parseInt(rawValue.trim());
    }
}