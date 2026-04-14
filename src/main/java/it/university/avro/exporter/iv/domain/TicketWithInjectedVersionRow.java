package it.university.avro.exporter.iv.domain;

public record TicketWithInjectedVersionRow(
        String ticketId,
        String createdDate,
        String closedDate,
        String openingVersion,
        String openingVersionDate,
        int affectedVersionCount,
        String affectedVersion,
        String fixedVersion,
        String fixedVersionDate,
        String injectedVersion
) {
}