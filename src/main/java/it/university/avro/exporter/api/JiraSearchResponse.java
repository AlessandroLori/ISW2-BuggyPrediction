package it.university.avro.exporter.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraSearchResponse(
        int startAt,
        int maxResults,
        int total,
        List<JiraIssueApiModel> issues
) {
}
