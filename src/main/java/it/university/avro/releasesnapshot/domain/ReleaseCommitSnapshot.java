package it.university.avro.releasesnapshot.domain;

import java.time.LocalDate;
import java.util.Objects;

public record ReleaseCommitSnapshot(
        String version,
        LocalDate releaseDate,
        String tagName,
        String commitHash
) {
    public ReleaseCommitSnapshot {
        version = Objects.requireNonNull(version, "version must not be null");
        releaseDate = Objects.requireNonNull(releaseDate, "releaseDate must not be null");
        tagName = Objects.requireNonNull(tagName, "tagName must not be null");
        commitHash = Objects.requireNonNull(commitHash, "commitHash must not be null");
    }
}