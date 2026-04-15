package it.university.avro.metrics.git;

public record GitCommandResult(
        int exitCode,
        String output
) {
    public boolean isSuccess() {
        return exitCode == 0;
    }
}