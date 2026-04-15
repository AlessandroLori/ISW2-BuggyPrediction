package it.university.avro.metrics.csv;

import java.util.ArrayList;
import java.util.List;

public final class SimpleCsvParser {

    public List<String> parseLine(final String line) {
        final List<String> values = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            final char currentChar = line.charAt(index);

            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (currentChar == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        values.add(current.toString());
        return List.copyOf(values);
    }
}