package it.university.avro.smelldataset.domain;

import java.util.List;

public record ReleaseMetricsTable(
        List<String> headers,
        List<ReleaseMetricsRow> rows
) {

    public ReleaseMetricsTable {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }
}
