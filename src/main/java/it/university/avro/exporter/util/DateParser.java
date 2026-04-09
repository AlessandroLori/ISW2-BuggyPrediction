package it.university.avro.exporter.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class DateParser {

    private static final DateTimeFormatter ISSUE_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private DateParser() {
        // Utility class
    }

    public static LocalDate parseIssueDate(final String value) {
        requireText(value, "Issue date value must not be blank");
        return OffsetDateTime.parse(value, ISSUE_DATE_TIME_FORMATTER).toLocalDate();
    }

    public static LocalDate parseVersionReleaseDate(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private static void requireText(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
