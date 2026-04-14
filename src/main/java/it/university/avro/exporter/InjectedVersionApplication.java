package it.university.avro.exporter;

import it.university.avro.exporter.iv.config.InjectedVersionConfiguration;
import it.university.avro.exporter.iv.csv.TicketDetailsCsvReader;
import it.university.avro.exporter.iv.csv.TicketDetailsWithIvWriter;
import it.university.avro.exporter.iv.service.InjectedVersionCalculator;
import it.university.avro.exporter.iv.service.InjectedVersionEnrichmentService;
import it.university.avro.exporter.iv.service.VersionOrderCatalogFactory;

public final class InjectedVersionApplication {

    private InjectedVersionApplication() {
        // Utility class
    }

    public static void main(final String[] args) {

        //Main per il calcolo della P e dell'IV

        final InjectedVersionConfiguration configuration = InjectedVersionConfiguration.defaultConfiguration();

        final TicketDetailsCsvReader reader = new TicketDetailsCsvReader(configuration.inputCsvPath());
        final TicketDetailsWithIvWriter writer = new TicketDetailsWithIvWriter(configuration.outputCsvPath());
        final VersionOrderCatalogFactory catalogFactory = new VersionOrderCatalogFactory();
        final InjectedVersionCalculator calculator = new InjectedVersionCalculator(catalogFactory);

        final InjectedVersionEnrichmentService service = new InjectedVersionEnrichmentService(
                reader,
                calculator,
                writer
        );

        service.enrich();
    }
}