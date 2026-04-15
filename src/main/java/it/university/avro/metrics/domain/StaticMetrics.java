package it.university.avro.metrics.domain;

public record StaticMetrics(
        int loc,
        int commentLines
) {
    public static StaticMetrics empty() {
        return new StaticMetrics(0, 0);
    }
}