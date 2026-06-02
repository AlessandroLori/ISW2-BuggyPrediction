package it.university.avro.metrics.csv;

import java.util.ArrayList;
import java.util.List;

public final class SimpleCsvParser {

    public List<String> parseLine(final String line) {
        final List<String> values = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        boolean inQuotes = false;
        int index = 0;
        while (index < line.length()) {
            final char currentChar = line.charAt(index);
            if (currentChar == '"' && isEscapedQuote(line, inQuotes, index)) {
                current.append('"');
                index += 2;
            } else if (currentChar == '"') {
                inQuotes = !inQuotes;
                index++;
            } else if (currentChar == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                index++;
            } else {
                current.append(currentChar);
                index++;
            }
        }

        values.add(current.toString());
        return List.copyOf(values);
    }

    private boolean isEscapedQuote(final String line, final boolean inQuotes, final int index) {
        return inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"';
    }
}
