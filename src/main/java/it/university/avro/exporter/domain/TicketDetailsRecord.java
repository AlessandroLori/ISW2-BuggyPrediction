package it.university.avro.exporter.domain;

public record TicketDetailsRecord(
        String ticketId,
        String createdDate,
        String closedDate,
        String openingVersion,
        String openingVersionDate,
        String affectedVersions,
        int affectedVersionCount,
        String fixedVersions,
        String fixedVersionDate
) {
}
