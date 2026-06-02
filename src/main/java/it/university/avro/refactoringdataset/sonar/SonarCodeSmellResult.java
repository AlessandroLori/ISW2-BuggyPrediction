package it.university.avro.refactoringdataset.sonar;

import java.util.Objects;
import java.util.Optional;

public record SonarCodeSmellResult(
        Optional<String> componentKey,
        int codeSmellCount,
        SonarCodeSmellStatus status,
        String message
) {
    public SonarCodeSmellResult {
        componentKey = componentKey == null ? Optional.empty() : componentKey;
        status = Objects.requireNonNull(status, "status must not be null");
        message = message == null ? "" : message;
    }

    public static SonarCodeSmellResult available(String componentKey, int codeSmellCount) {
        return new SonarCodeSmellResult(
                Optional.of(componentKey),
                codeSmellCount,
                SonarCodeSmellStatus.AVAILABLE,
                ""
        );
    }

    public static SonarCodeSmellResult unavailable(
            Optional<String> componentKey,
            int unavailableValue,
            SonarCodeSmellStatus status,
            String message
    ) {
        return new SonarCodeSmellResult(componentKey, unavailableValue, status, message);
    }

    public boolean isAvailable() {
        return status == SonarCodeSmellStatus.AVAILABLE;
    }
}
