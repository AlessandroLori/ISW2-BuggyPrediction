package it.university.avro.weka.csv;

import it.university.avro.weka.domain.FinalWekaResultRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record FinalWekaResultsCsvWriter(Path outputCsvPath) {

    public void write(final List<FinalWekaResultRecord> records) {
        try {
            final Path parentDirectory = outputCsvPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputCsvPath)) {
                writer.write("Dataset,Classifier,FS,Balancing,Precision,Recall,AUC,Kappa,NPofB20");
                writer.newLine();

                for (FinalWekaResultRecord resultRecord : records) {
                    writer.write(csv(resultRecord.dataset()));
                    writer.write(",");
                    writer.write(csv(resultRecord.classifier()));
                    writer.write(",");
                    writer.write(csv(resultRecord.fs()));
                    writer.write(",");
                    writer.write(csv(resultRecord.balancing()));
                    writer.write(",");
                    writer.write(resultRecord.precisionAsCsv());
                    writer.write(",");
                    writer.write(resultRecord.recallAsCsv());
                    writer.write(",");
                    writer.write(resultRecord.aucAsCsv());
                    writer.write(",");
                    writer.write(resultRecord.kappaAsCsv());
                    writer.write(",");
                    writer.write(csv(resultRecord.npofb20()));
                    writer.newLine();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write final Weka results csv: " + outputCsvPath, exception);
        }
    }
    private String csv(final String value) {
        final String safeValue = value == null ? "" : value;
        final String escaped = safeValue.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
