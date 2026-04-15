package it.university.avro.releasesnapshot.domain;

import java.time.LocalDate;
import java.util.Objects;

public record ReleaseInfo(
        String version,
        LocalDate releaseDate
) {
    public ReleaseInfo {
        version = Objects.requireNonNull(version, "version must not be null");
        releaseDate = Objects.requireNonNull(releaseDate, "releaseDate must not be null");
    }
}