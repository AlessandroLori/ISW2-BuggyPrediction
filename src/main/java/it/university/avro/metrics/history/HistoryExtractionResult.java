package it.university.avro.metrics.history;

import it.university.avro.metrics.domain.HistoryMetrics;

import java.util.Objects;

public record HistoryExtractionResult(
        HistoryMetrics metrics,
        boolean hasWindowCommits,
        boolean hasCumulativeCommits
) {
    public HistoryExtractionResult {
        metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }
}