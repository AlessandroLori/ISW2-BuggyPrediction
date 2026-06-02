package it.university.avro.metrics.csv;

import it.university.avro.metrics.domain.BugTicket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TicketDetailsBugIdReader {

    private final SimpleCsvParser csvParser = new SimpleCsvParser();

    public Map<String, BugTicket> readTickets(final Path csvPath) {
        try {
            final List<String> lines = Files.readAllLines(csvPath);

            if (lines.isEmpty()) {
                return Map.of();
            }

            final List<String> header = csvParser.parseLine(lines.get(0));

            final int ticketIdIndex = findRequiredIndex(header, "ticket id");
            final int createDateIndex = findRequiredIndex(header, "create date");
            final int closedDateIndex = findRequiredIndex(header, "closed date");
            final int injectedVersionIndex = findOptionalIndex(header, "injected version");
            final int fixedVersionIndex = findOptionalIndex(header, "fixed version");

            final Map<String, BugTicket> tickets = new LinkedHashMap<>();

            for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
                parseTicketLine(
                        lines.get(lineIndex),
                        ticketIdIndex,
                        createDateIndex,
                        closedDateIndex,
                        injectedVersionIndex,
                        fixedVersionIndex
                ).ifPresent(ticket -> tickets.put(ticket.ticketId(), ticket));
            }

            return Map.copyOf(tickets);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read TicketDetails csv " + csvPath, exception);
        }
    }


    private Optional<BugTicket> parseTicketLine(
            final String rawLine,
            final int ticketIdIndex,
            final int createDateIndex,
            final int closedDateIndex,
            final int injectedVersionIndex,
            final int fixedVersionIndex
    ) {
        if (rawLine.isBlank()) {
            return Optional.empty();
        }

        final List<String> values = csvParser.parseLine(rawLine);
        if (hasMissingRequiredValue(values, ticketIdIndex, createDateIndex, closedDateIndex)) {
            return Optional.empty();
        }

        final String ticketId = values.get(ticketIdIndex).trim().toUpperCase();
        final String createDateRaw = values.get(createDateIndex).trim();
        final String closedDateRaw = values.get(closedDateIndex).trim();
        if (ticketId.isBlank() || createDateRaw.isBlank() || closedDateRaw.isBlank()) {
            return Optional.empty();
        }

        final String injectedVersion = readOptionalValue(values, injectedVersionIndex);
        final String fixedVersion = readOptionalValue(values, fixedVersionIndex);
        final LocalDate creationDate = parseLocalDate(createDateRaw);
        final LocalDate closedDate = parseLocalDate(closedDateRaw);
        return Optional.of(new BugTicket(ticketId, creationDate, closedDate, injectedVersion, fixedVersion));
    }

    private boolean hasMissingRequiredValue(
            final List<String> values,
            final int ticketIdIndex,
            final int createDateIndex,
            final int closedDateIndex
    ) {
        return ticketIdIndex >= values.size()
                || createDateIndex >= values.size()
                || closedDateIndex >= values.size();
    }

    private int findRequiredIndex(final List<String> header, final String columnName) {
        final int index = header.indexOf(columnName);
        if (index < 0) {
            throw new IllegalStateException("Missing required column in TicketDetails csv: " + columnName);
        }
        return index;
    }

    private int findOptionalIndex(final List<String> header, final String columnName) {
        return header.indexOf(columnName);
    }

    private String readOptionalValue(final List<String> values, final int index) {
        if (index < 0 || index >= values.size()) {
            return "";
        }
        return values.get(index).trim();
    }

    private LocalDate parseLocalDate(final String rawValue) {
        final String normalized = rawValue.trim();

        if (normalized.length() >= 10) {
            return LocalDate.parse(normalized.substring(0, 10));
        }

        return LocalDate.parse(normalized);
    }
}