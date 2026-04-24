package it.university.avro.weka.io;

import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TrainingInstancesLoader {

    public Instances load(final Path csvPath) throws IOException {
        if (!Files.exists(csvPath)) {
            throw new IllegalStateException("Input csv not found: " + csvPath);
        }

        final CSVLoader loader = new CSVLoader();
        loader.setSource(csvPath.toFile());

        final Instances instances = loader.getDataSet();
        if (instances.numAttributes() < 2) {
            throw new IllegalStateException(
                    "The training csv must contain at least one feature and the BUGGY class attribute"
            );
        }

        instances.setClassIndex(instances.numAttributes() - 1);
        validateClassAttribute(instances.classAttribute(), csvPath);
        return instances;
    }

    private void validateClassAttribute(final Attribute classAttribute, final Path csvPath) {
        if (!"BUGGY".equalsIgnoreCase(classAttribute.name())) {
            throw new IllegalStateException(
                    "Expected BUGGY as last column in " + csvPath + " but found " + classAttribute.name()
            );
        }

        if (!classAttribute.isNominal()) {
            throw new IllegalStateException(
                    "BUGGY must be nominal in " + csvPath + " so Weka can run classification-oriented wrapper selection"
            );
        }
    }
}
