package it.university.avro.smelldataset.csv;

import it.university.avro.smelldataset.domain.ReleaseMetricsRow;
import it.university.avro.smelldataset.domain.ReleaseMetricsTable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReleaseMetricsCsvReader {

    public ReleaseMetricsTable read(Path inputPath) throws IOException {
        validateInput(inputPath);

        try (
                BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(reader)
        ) {
            List<String> headers = parser.getHeaderNames();
            List<ReleaseMetricsRow> rows = new ArrayList<>();

            for (CSVRecord csvRecord : parser) {
                rows.add(toRow(headers, csvRecord));
            }

            return new ReleaseMetricsTable(headers, rows);
        }
    }

    private static void validateInput(Path inputPath) {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input CSV not found: " + inputPath);
        }

        if (!Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("Input path is not a regular file: " + inputPath);
        }
    }

    private static ReleaseMetricsRow toRow(List<String> headers, CSVRecord csvRecord) {
        Map<String, String> valuesByHeader = new LinkedHashMap<>();

        for (String header : headers) {
            valuesByHeader.put(header, csvRecord.get(header));
        }

        return new ReleaseMetricsRow(valuesByHeader);
    }
}
