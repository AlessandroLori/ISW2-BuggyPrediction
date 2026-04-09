package it.university.avro.exporter.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraVersionReferenceApiModel(
        String id,
        String name
) {
}
