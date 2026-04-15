package it.university.avro.metrics.csv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TicketDetailsBugIdReader {

    private final SimpleCsvParser csvParser = new SimpleCsvParser();

    public Set<String> readBugIds(final Path csvPath) {
        try {
            final List<String> lines = Files.readAllLines(csvPath);

            if (lines.isEmpty()) {
                return Set.of();
            }

            final List<String> header = csvParser.parseLine(lines.get(0));
            int ticketIdIndex = header.indexOf("ticket id");
            if (ticketIdIndex < 0) {
                ticketIdIndex = 0;
            }

            final Set<String> bugIds = new LinkedHashSet<>();

            for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
                final String rawLine = lines.get(lineIndex);
                if (rawLine.isBlank()) {
                    continue;
                }

                final List<String> values = csvParser.parseLine(rawLine);
                if (ticketIdIndex >= values.size()) {
                    continue;
                }

                final String ticketId = values.get(ticketIdIndex).trim();
                if (!ticketId.isBlank()) {
                    bugIds.add(ticketId.toUpperCase());
                }
            }

            return Set.copyOf(bugIds);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read TicketDetails csv " + csvPath, exception);
        }
    }
}