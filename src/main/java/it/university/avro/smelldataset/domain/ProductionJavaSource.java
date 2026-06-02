package it.university.avro.smelldataset.domain;

import java.util.Objects;

public record ProductionJavaSource(
        String classPath,
        String sourceCode
) {

    public ProductionJavaSource {
        classPath = Objects.requireNonNull(classPath, "classPath must not be null").replace('\\', '/');
        sourceCode = Objects.requireNonNull(sourceCode, "sourceCode must not be null");
    }
}
