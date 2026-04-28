package it.university.avro.weka.csv;

import it.university.avro.weka.domain.FinalWekaResultRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FinalWekaResultsCsvWriter {

    private final Path outputCsvPath;

    public FinalWekaResultsCsvWriter(final Path outputCsvPath) {
        this.outputCsvPath = outputCsvPath;
    }

    public void write(final List<FinalWekaResultRecord> records) {
        try {
            final Path parentDirectory = outputCsvPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputCsvPath)) {
                writer.write("Dataset,Classifier,FS,Balancing,Precision,Recall,AUC,Kappa,NPofB20");
                writer.newLine();

                for (FinalWekaResultRecord record : records) {
                    writer.write(csv(record.dataset()));
                    writer.write(",");
                    writer.write(csv(record.classifier()));
                    writer.write(",");
                    writer.write(csv(record.fs()));
                    writer.write(",");
                    writer.write(csv(record.balancing()));
                    writer.write(",");
                    writer.write(record.precisionAsCsv());
                    writer.write(",");
                    writer.write(record.recallAsCsv());
                    writer.write(",");
                    writer.write(record.aucAsCsv());
                    writer.write(",");
                    writer.write(record.kappaAsCsv());
                    writer.write(",");
                    writer.write(csv(record.npofb20()));
                    writer.newLine();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write final Weka results csv: " + outputCsvPath, exception);
        }
    }

    public Path outputCsvPath() {
        return outputCsvPath;
    }

    private String csv(final String value) {
        final String safeValue = value == null ? "" : value;
        final String escaped = safeValue.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
