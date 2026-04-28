package it.university.avro.weka.domain;

public record WekaConfigurationKey(
        String dataset,
        String classifier,
        String fs,
        String balancing
) {
}
