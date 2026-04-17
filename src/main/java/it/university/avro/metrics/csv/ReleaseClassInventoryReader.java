package it.university.avro.metrics.csv;

import it.university.avro.metrics.domain.InventoryRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReleaseClassInventoryReader {

    private final SimpleCsvParser csvParser = new SimpleCsvParser();

    public List<InventoryRecord> read(final Path csvPath) {
        try {
            final List<String> lines = Files.readAllLines(csvPath);

            if (lines.isEmpty()) {
                return List.of();
            }

            final List<String> header = csvParser.parseLine(lines.get(0));
            final int versionIndex = header.indexOf("version");
            final int classPathIndex = header.indexOf("classpath");

            if (versionIndex < 0 || classPathIndex < 0) {
                throw new IllegalStateException("ReleaseClassInventory header must contain version and classpath");
            }

            final List<InventoryRecord> records = new ArrayList<>();

            for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
                final String rawLine = lines.get(lineIndex);
                if (rawLine.isBlank()) {
                    continue;
                }

                final List<String> values = csvParser.parseLine(rawLine);
                final String version = values.get(versionIndex).trim();
                final String classPath = values.get(classPathIndex).trim();

                if (version.isBlank() || classPath.isBlank()) {
                    continue;
                }

                records.add(new InventoryRecord(
                        version,
                        classPath.replace('\\', '/')
                ));
            }

            return List.copyOf(deduplicate(records));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read inventory csv " + csvPath, exception);
        }
    }

    private List<InventoryRecord> deduplicate(final List<InventoryRecord> records) {
        final Map<String, InventoryRecord> uniqueRecords = new LinkedHashMap<>();

        for (InventoryRecord record : records) {
            final String key = record.version() + "|" + record.classPath();
            final InventoryRecord previous = uniqueRecords.putIfAbsent(key, record);

            if (previous != null) {
                System.out.println(
                        "[DROP-DUPLICATE-METRICS-INPUT] release=" + record.version()
                                + " | path=" + record.classPath()
                );
            }
        }

        return new ArrayList<>(uniqueRecords.values());
    }
}