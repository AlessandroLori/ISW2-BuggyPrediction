package it.university.avro.datasetprep.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TabularDataset(
        List<String> headers,
        List<Map<String, String>> rows
) {

    public TabularDataset {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("headers must not be null or empty");
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows must not be null");
        }

        headers = List.copyOf(headers);
        rows = rows.stream()
                .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                .toList();
    }
}