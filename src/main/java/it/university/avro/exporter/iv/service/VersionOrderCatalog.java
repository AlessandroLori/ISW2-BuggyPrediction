package it.university.avro.exporter.iv.service;

import java.util.Map;
import java.util.Objects;

public final class VersionOrderCatalog {

    private final Map<String, Integer> versionToIndex;
    private final Map<Integer, String> indexToVersion;

    public VersionOrderCatalog(
            final Map<String, Integer> versionToIndex,
            final Map<Integer, String> indexToVersion
    ) {
        this.versionToIndex = Map.copyOf(Objects.requireNonNull(versionToIndex, "versionToIndex must not be null"));
        this.indexToVersion = Map.copyOf(Objects.requireNonNull(indexToVersion, "indexToVersion must not be null"));
    }

    public int positionOf(final String versionName) {
        final Integer position = versionToIndex.get(versionName);
        if (position == null) {
            throw new IllegalArgumentException("Unknown version: " + versionName);
        }
        return position;
    }

    public String versionAtPosition(final int position) {
        final String version = indexToVersion.get(position);
        if (version == null) {
            throw new IllegalArgumentException("No version mapped at position: " + position);
        }
        return version;
    }

    public int maxPosition() {
        return indexToVersion.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElseThrow(() -> new IllegalStateException("Version catalog is empty"));
    }

}