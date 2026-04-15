package it.university.avro.metrics.domain;

public record HistoryMetrics(
        int revs,
        int fixes,
        int auth,
        int locTouched,
        int locAdded,
        int maxLocAdded,
        double avgLocAdded,
        int churn,
        int maxChurn,
        double avgChurn
) {
    public static HistoryMetrics empty() {
        return new HistoryMetrics(0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0.0);
    }
}