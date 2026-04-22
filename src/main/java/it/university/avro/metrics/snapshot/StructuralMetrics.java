package it.university.avro.metrics.snapshot;

public record StructuralMetrics(
        int nestingDepth,
        int decisionPoints
) {
    public static StructuralMetrics empty() {
        return new StructuralMetrics(0, 0);
    }
}
