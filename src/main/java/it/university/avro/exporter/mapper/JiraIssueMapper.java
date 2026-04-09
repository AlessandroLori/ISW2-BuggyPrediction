package it.university.avro.exporter.mapper;

import it.university.avro.exporter.api.JiraIssueApiModel;
import it.university.avro.exporter.api.JiraVersionReferenceApiModel;
import it.university.avro.exporter.domain.JiraIssue;
import it.university.avro.exporter.domain.VersionReference;
import it.university.avro.exporter.util.DateParser;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class JiraIssueMapper {

    public JiraIssue toDomain(final JiraIssueApiModel apiModel) {
        Objects.requireNonNull(apiModel, "apiModel must not be null");
        final JiraIssueApiModel.JiraIssueFieldsApiModel fields = apiModel.fields();
        if (fields == null) {
            throw new IllegalArgumentException("Issue fields must not be null");
        }

        return new JiraIssue(
                apiModel.key(),
                DateParser.parseIssueDate(fields.created()),
                DateParser.parseIssueDate(fields.resolutiondate()),
                mapReferences(fields.versions()),
                mapReferences(fields.fixVersions())
        );
    }

    private List<VersionReference> mapReferences(final List<JiraVersionReferenceApiModel> references) {
        if (references == null || references.isEmpty()) {
            return Collections.emptyList();
        }
        return references.stream()
                .map(reference -> new VersionReference(reference.id(), reference.name()))
                .toList();
    }
}
