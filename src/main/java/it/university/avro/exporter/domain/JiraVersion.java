package it.university.avro.exporter.domain;

import java.time.LocalDate;

public record JiraVersion(
        String id,
        String name,
        boolean released,
        LocalDate releaseDate
) {
}
