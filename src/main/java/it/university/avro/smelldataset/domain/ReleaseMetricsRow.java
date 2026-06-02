package it.university.avro.smelldataset.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReleaseMetricsRow(Map<String, String> valuesByHeader) {

    public ReleaseMetricsRow {
        valuesByHeader = Map.copyOf(valuesByHeader);
    }

    public String value(String header) {
        return valuesByHeader.getOrDefault(header, "");
    }

    public Map<String, String> asOrderedMap(Iterable<String> headers) {
        LinkedHashMap<String, String> orderedValues = new LinkedHashMap<>();

        for (String header : headers) {
            orderedValues.put(header, value(header));
        }

        return orderedValues;
    }
}
