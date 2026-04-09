package it.university.avro.exporter.service;

import it.university.avro.exporter.client.JiraClient;
import it.university.avro.exporter.csv.TicketDetailsWriter;
import it.university.avro.exporter.domain.TicketDetailsRecord;
import it.university.avro.exporter.domain.VersionCatalog;
import it.university.avro.exporter.mapper.JiraIssueMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TicketDetailsExportService {

    private final JiraClient jiraClient;
    private final VersionCatalogService versionCatalogService;
    private final TicketDetailsFactory ticketDetailsFactory;
    private final TicketDetailsWriter ticketDetailsWriter;
    private final JiraIssueMapper jiraIssueMapper;

    public TicketDetailsExportService(
            final JiraClient jiraClient,
            final VersionCatalogService versionCatalogService,
            final TicketDetailsFactory ticketDetailsFactory,
            final TicketDetailsWriter ticketDetailsWriter
    ) {
        this(jiraClient, versionCatalogService, ticketDetailsFactory, ticketDetailsWriter, new JiraIssueMapper());
    }

    TicketDetailsExportService(
            final JiraClient jiraClient,
            final VersionCatalogService versionCatalogService,
            final TicketDetailsFactory ticketDetailsFactory,
            final TicketDetailsWriter ticketDetailsWriter,
            final JiraIssueMapper jiraIssueMapper
    ) {
        this.jiraClient = Objects.requireNonNull(jiraClient, "jiraClient must not be null");
        this.versionCatalogService = Objects.requireNonNull(versionCatalogService,
                "versionCatalogService must not be null");
        this.ticketDetailsFactory = Objects.requireNonNull(ticketDetailsFactory,
                "ticketDetailsFactory must not be null");
        this.ticketDetailsWriter = Objects.requireNonNull(ticketDetailsWriter,
                "ticketDetailsWriter must not be null");
        this.jiraIssueMapper = Objects.requireNonNull(jiraIssueMapper, "jiraIssueMapper must not be null");
    }

    public void export() {
        final VersionCatalog versionCatalog = versionCatalogService.loadCatalog();
        final List<TicketDetailsRecord> records = jiraClient.fetchBugIssues().stream()
                .map(jiraIssueMapper::toDomain)
                .map(issue -> ticketDetailsFactory.create(issue, versionCatalog))
                .flatMap(Optional::stream)
                .toList();

        ticketDetailsWriter.write(records);
    }
}
