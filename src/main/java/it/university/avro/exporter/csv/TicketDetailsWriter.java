package it.university.avro.exporter.csv;

import it.university.avro.exporter.domain.TicketDetailsRecord;

import java.util.List;

public interface TicketDetailsWriter {

    void write(List<TicketDetailsRecord> records);
}
