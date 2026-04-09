package it.university.avro.exporter.service;

import it.university.avro.exporter.client.JiraClient;
import it.university.avro.exporter.domain.VersionCatalog;
import it.university.avro.exporter.mapper.JiraVersionMapper;

import java.util.Objects;

public final class VersionCatalogService {

    private final JiraClient jiraClient;
    private final JiraVersionMapper jiraVersionMapper;

    public VersionCatalogService(final JiraClient jiraClient) {
        this(jiraClient, new JiraVersionMapper());
    }

    VersionCatalogService(final JiraClient jiraClient, final JiraVersionMapper jiraVersionMapper) {
        this.jiraClient = Objects.requireNonNull(jiraClient, "jiraClient must not be null");
        this.jiraVersionMapper = Objects.requireNonNull(jiraVersionMapper, "jiraVersionMapper must not be null");
    }

    public VersionCatalog loadCatalog() {
        return new VersionCatalog(jiraVersionMapper.toDomain(jiraClient.fetchProjectVersions()));
    }
}
