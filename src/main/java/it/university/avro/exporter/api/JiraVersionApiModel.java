package it.university.avro.exporter.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraVersionApiModel(
        String id,
        String name,
        boolean released,
        String releaseDate
) {
}
