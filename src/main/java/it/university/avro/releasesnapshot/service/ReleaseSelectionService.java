package it.university.avro.releasesnapshot.service;

import it.university.avro.releasesnapshot.domain.ReleaseInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ReleaseSelectionService {

    public List<ReleaseInfo> selectOldestThird(final List<ReleaseInfo> releases) {
        Objects.requireNonNull(releases, "releases must not be null");

        final List<ReleaseInfo> ordered = releases.stream()
                .sorted(Comparator.comparing(ReleaseInfo::releaseDate).thenComparing(ReleaseInfo::version))
                .toList();

        if (ordered.isEmpty()) {
            return List.of();
        }

        final int keepCount = Math.max(1, (int) Math.ceil(ordered.size() / 3.0d));
        return ordered.subList(0, keepCount);
    }
}