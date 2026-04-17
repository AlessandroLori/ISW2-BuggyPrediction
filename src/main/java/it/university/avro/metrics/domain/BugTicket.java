package it.university.avro.metrics.domain;

import java.time.LocalDate;
import java.util.Objects;

public record BugTicket(
        String ticketId,
        LocalDate creationDate,
        LocalDate closedDate
) {
    public BugTicket {
        ticketId = Objects.requireNonNull(ticketId, "ticketId must not be null");
        creationDate = Objects.requireNonNull(creationDate, "creationDate must not be null");
        closedDate = Objects.requireNonNull(closedDate, "closedDate must not be null");
    }
}