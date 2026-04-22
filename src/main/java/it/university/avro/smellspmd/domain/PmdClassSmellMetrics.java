package it.university.avro.smellspmd.domain;

public record PmdClassSmellMetrics(
        int smellCount,
        int distinctSmellTypes
) {
    public static PmdClassSmellMetrics empty() {
        return new PmdClassSmellMetrics(0, 0);
    }
}
