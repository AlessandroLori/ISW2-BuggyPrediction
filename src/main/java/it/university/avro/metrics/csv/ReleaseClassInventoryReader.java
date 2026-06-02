package it.university.avro.metrics.csv;

import it.university.avro.common.ApplicationLog;

import it.university.avro.metrics.domain.InventoryRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                parseInventoryLine(lines.get(lineIndex), versionIndex, classPathIndex).ifPresent(records::add);
            }

            return List.copyOf(deduplicate(records));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read inventory csv " + csvPath, exception);
        }
    }


    private Optional<InventoryRecord> parseInventoryLine(
            final String rawLine,
            final int versionIndex,
            final int classPathIndex
    ) {
        if (rawLine.isBlank()) {
            return Optional.empty();
        }

        final List<String> values = csvParser.parseLine(rawLine);
        final String version = values.get(versionIndex).trim();
        final String classPath = values.get(classPathIndex).trim();
        if (version.isBlank() || classPath.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new InventoryRecord(version, classPath.replace('\\', '/')));
    }

    private List<InventoryRecord> deduplicate(final List<InventoryRecord> records) {
        final Map<String, InventoryRecord> uniqueRecords = new LinkedHashMap<>();

        for (InventoryRecord inventoryRecord : records) {
            final String key = inventoryRecord.version() + "|" + inventoryRecord.classPath();
            final InventoryRecord previous = uniqueRecords.putIfAbsent(key, inventoryRecord);

            if (previous != null) {
                ApplicationLog.info(
                        "[DROP-DUPLICATE-METRICS-INPUT] release=" + inventoryRecord.version()
                                + " | path=" + inventoryRecord.classPath()
                );
            }
        }

        return new ArrayList<>(uniqueRecords.values());
    }
}
