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


    /*
        Proporton:
        bene, benissimo, ora dobbiamo applicare il concetto di proportion ovvero per tutti i ticket che abbiamo nel csv
        che non hanno l'affected version ovvero sono marcati con n/a. Quello che dobbiamo fare è usare un approccio
        total per stimare la P di proportion sui ticket che hanno già l'affected version valida assumendo come injected
         verison l'affected version ovvero IV = AV per poi calcolare P ticket per ticket, fare la media e usare il P
         finale per calcolare le AV dei ticket mancanti. Conosci le formule?

     */

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
