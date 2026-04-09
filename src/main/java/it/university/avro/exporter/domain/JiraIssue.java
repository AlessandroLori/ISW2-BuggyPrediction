package it.university.avro.exporter.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record JiraIssue(
        String ticketId,
        LocalDate createdDate,
        LocalDate closedDate,
        List<VersionReference> affectedVersionReferences,
        List<VersionReference> fixedVersionReferences
) {

    public JiraIssue {
        requireText(ticketId, "ticketId");
        createdDate = Objects.requireNonNull(createdDate, "createdDate must not be null");
        closedDate = Objects.requireNonNull(closedDate, "closedDate must not be null");
        affectedVersionReferences = List.copyOf(Objects.requireNonNull(affectedVersionReferences,
                "affectedVersionReferences must not be null"));
        fixedVersionReferences = List.copyOf(Objects.requireNonNull(fixedVersionReferences,
                "fixedVersionReferences must not be null"));
    }

    private static void requireText(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
