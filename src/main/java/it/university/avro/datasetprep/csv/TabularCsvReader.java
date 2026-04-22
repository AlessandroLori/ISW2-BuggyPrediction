package it.university.avro.datasetprep.csv;

import it.university.avro.datasetprep.domain.TabularDataset;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TabularCsvReader {

    public TabularDataset read(final Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            final List<String> headers = new ArrayList<>(parser.getHeaderMap().keySet());
            final List<Map<String, String>> rows = new ArrayList<>();

            for (CSVRecord record : parser) {
                final Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, record.get(header));
                }
                rows.add(row);
            }

            return new TabularDataset(headers, rows);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read csv: " + csvPath, exception);
        }
    }
}