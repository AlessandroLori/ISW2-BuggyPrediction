package it.university.avro.exporter.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueApiModel(
        String id,
        String key,
        JiraIssueFieldsApiModel fields
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraIssueFieldsApiModel(
            String created,
            String resolutiondate,
            List<JiraVersionReferenceApiModel> versions,
            List<JiraVersionReferenceApiModel> fixVersions
    ) {
    }
}
