package it.university.avro.exporter.iv.domain;

public record TicketCsvRow(
        String ticketId,
        String createdDate,
        String closedDate,
        String openingVersion,
        String openingVersionDate,
        int affectedVersionCount,
        String affectedVersion,
        String fixedVersion,
        String fixedVersionDate
) {
}