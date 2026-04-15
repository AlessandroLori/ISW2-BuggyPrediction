package it.university.avro.releasesnapshot.csv;

import it.university.avro.releasesnapshot.domain.ReleaseInfo;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TicketDetailsReleaseCatalogReader {

    private static final String NOT_AVAILABLE = "n/a";

    private final Path ticketDetailsCsvPath;

    public TicketDetailsReleaseCatalogReader(final Path ticketDetailsCsvPath) {
        this.ticketDetailsCsvPath = Objects.requireNonNull(ticketDetailsCsvPath, "ticketDetailsCsvPath must not be null");
    }

    public List<ReleaseInfo> readReleases() {
        final Map<String, ReleaseInfo> releasesByVersion = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(ticketDetailsCsvPath);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                addReleaseIfPresent(
                        releasesByVersion,
                        getValue(record, "opening version", "opening_version"),
                        getValue(record, "opening version date", "opening_version_date")
                );

                addReleaseIfPresent(
                        releasesByVersion,
                        getValue(record, "fixed version", "fixed_versions", "fixed version"),
                        getValue(record, "fixed version date", "fixed_version_date")
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read releases from " + ticketDetailsCsvPath, exception);
        }

        return List.copyOf(releasesByVersion.values());
    }

    private void addReleaseIfPresent(
            final Map<String, ReleaseInfo> releasesByVersion,
            final String version,
            final String date
    ) {
        if (isNotAvailable(version) || isNotAvailable(date)) {
            return;
        }

        releasesByVersion.putIfAbsent(
                version.trim(),
                new ReleaseInfo(version.trim(), LocalDate.parse(date.trim()))
        );
    }

    private boolean isNotAvailable(final String value) {
        return value == null || value.isBlank() || NOT_AVAILABLE.equalsIgnoreCase(value.trim());
    }

    private String getValue(final CSVRecord record, final String... headerCandidates) {
        for (String header : headerCandidates) {
            if (record.isMapped(header)) {
                return record.get(header);
            }
        }
        throw new IllegalArgumentException("Missing expected CSV header");
    }
}