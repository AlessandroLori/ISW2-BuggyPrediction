package it.university.avro.exporter.iv.service;

import it.university.avro.exporter.iv.csv.TicketDetailsCsvReader;
import it.university.avro.exporter.iv.csv.TicketDetailsWithIvWriter;
import it.university.avro.exporter.iv.domain.TicketCsvRow;
import it.university.avro.exporter.iv.domain.TicketWithInjectedVersionRow;

import java.util.List;
import java.util.Objects;

public final class InjectedVersionEnrichmentService {

    private final TicketDetailsCsvReader reader;
    private final InjectedVersionCalculator calculator;
    private final TicketDetailsWithIvWriter writer;

    public InjectedVersionEnrichmentService(
            final TicketDetailsCsvReader reader,
            final InjectedVersionCalculator calculator,
            final TicketDetailsWithIvWriter writer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator must not be null");
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
    }

    public void enrich() {
        final List<TicketCsvRow> rows = reader.read();
        final List<TicketWithInjectedVersionRow> enrichedRows = calculator.enrich(rows);
        writer.write(enrichedRows);
    }
}