package it.university.avro.exporter;

import it.university.avro.exporter.client.ApacheJiraClient;
import it.university.avro.exporter.client.JiraClient;
import it.university.avro.exporter.config.ExporterConfiguration;
import it.university.avro.exporter.csv.CsvTicketDetailsWriter;
import it.university.avro.exporter.csv.TicketDetailsWriter;
import it.university.avro.exporter.service.TicketDetailsExportService;
import it.university.avro.exporter.service.TicketDetailsFactory;
import it.university.avro.exporter.service.VersionCatalogService;

public final class TicketDetailsApplication {

    private TicketDetailsApplication() {
        // Utility class
    }

    public static void main(final String[] args) {
        final ExporterConfiguration configuration = ExporterConfiguration.defaultConfiguration();
        final JiraClient jiraClient = new ApacheJiraClient(configuration);
        final VersionCatalogService versionCatalogService = new VersionCatalogService(jiraClient);
        final TicketDetailsFactory ticketDetailsFactory = new TicketDetailsFactory();
        final TicketDetailsWriter ticketDetailsWriter = new CsvTicketDetailsWriter(configuration.outputCsvPath());
        final TicketDetailsExportService exportService = new TicketDetailsExportService(
                jiraClient,
                versionCatalogService,
                ticketDetailsFactory,
                ticketDetailsWriter
        );

        exportService.export();
    }
}
