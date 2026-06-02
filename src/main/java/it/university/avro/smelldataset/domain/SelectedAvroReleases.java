package it.university.avro.smelldataset.domain;

public record SelectedAvroReleases(
        AvroRelease latestRelease,
        AvroRelease previousRelease
) {
}
