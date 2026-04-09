package it.university.avro.exporter.mapper;

import it.university.avro.exporter.api.JiraVersionApiModel;
import it.university.avro.exporter.domain.JiraVersion;
import it.university.avro.exporter.util.DateParser;

import java.util.List;
import java.util.Objects;

public final class JiraVersionMapper {

    public List<JiraVersion> toDomain(final List<JiraVersionApiModel> apiModels) {
        Objects.requireNonNull(apiModels, "apiModels must not be null");
        return apiModels.stream()
                .map(this::toDomain)
                .toList();
    }

    public JiraVersion toDomain(final JiraVersionApiModel apiModel) {
        Objects.requireNonNull(apiModel, "apiModel must not be null");
        return new JiraVersion(
                apiModel.id(),
                apiModel.name(),
                apiModel.released(),
                DateParser.parseVersionReleaseDate(apiModel.releaseDate())
        );
    }
}
