package it.university.avro.exporter.iv.service;

import it.university.avro.exporter.iv.domain.TicketCsvRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class VersionOrderCatalogFactory {

    private static final String NOT_AVAILABLE = "n/a";

    public VersionOrderCatalog create(final List<TicketCsvRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");

        final Set<String> orderedVersions = new TreeSet<>(new VersionNameComparator());

        for (TicketCsvRow row : rows) {
            addVersionIfPresent(orderedVersions, row.openingVersion());
            addVersionIfPresent(orderedVersions, row.fixedVersion());

            for (String affectedVersion : splitAffectedVersions(row.affectedVersion())) {
                addVersionIfPresent(orderedVersions, affectedVersion);
            }
        }

        final List<String> versionList = new ArrayList<>(orderedVersions);
        final LinkedHashMap<String, Integer> versionToIndex = new LinkedHashMap<>();
        final LinkedHashMap<Integer, String> indexToVersion = new LinkedHashMap<>();

        for (int index = 0; index < versionList.size(); index++) {
            final int position = index + 1;
            final String version = versionList.get(index);
            versionToIndex.put(version, position);
            indexToVersion.put(position, version);
        }

        return new VersionOrderCatalog(versionToIndex, indexToVersion);
    }

    private void addVersionIfPresent(final Set<String> versions, final String version) {
        if (version == null || version.isBlank() || NOT_AVAILABLE.equalsIgnoreCase(version.trim())) {
            return;
        }
        versions.add(version.trim());
    }

    private List<String> splitAffectedVersions(final String affectedVersions) {
        if (affectedVersions == null || affectedVersions.isBlank() || NOT_AVAILABLE.equalsIgnoreCase(affectedVersions.trim())) {
            return List.of();
        }

        final String[] tokens = affectedVersions.split(";");
        final List<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                result.add(token.trim());
            }
        }
        return List.copyOf(result);
    }
}