package it.university.avro.smelldataset.release;

import it.university.avro.smelldataset.domain.AvroRelease;
import it.university.avro.smelldataset.domain.SelectedAvroReleases;
import it.university.avro.smelldataset.util.SemanticVersionComparator;

import java.util.List;

public final class LatestOfficialAvroReleaseSelector {

    public SelectedAvroReleases select(final List<AvroRelease> officialReleases) {
        if (officialReleases == null || officialReleases.isEmpty()) {
            throw new IllegalArgumentException("officialReleases must not be empty");
        }

        final List<AvroRelease> ordered = officialReleases.stream()
                .sorted((first, second) -> SemanticVersionComparator.INSTANCE.compare(
                        first.version(),
                        second.version()
                ))
                .toList();

        final AvroRelease latest = ordered.get(ordered.size() - 1);
        final AvroRelease previous = ordered.size() >= 2 ? ordered.get(ordered.size() - 2) : null;

        return new SelectedAvroReleases(latest, previous);
    }
}
