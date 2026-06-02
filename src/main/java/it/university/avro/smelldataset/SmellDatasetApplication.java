package it.university.avro.smelldataset;

import it.university.avro.smelldataset.config.SmellDatasetConfiguration;
import it.university.avro.smelldataset.service.SmellDatasetGenerationService;

public final class SmellDatasetApplication {

    private SmellDatasetApplication() {
        // Application entry point holder.
    }

    public static void main(final String[] args) {
        final SmellDatasetConfiguration configuration = SmellDatasetConfiguration.fromArgs(args);
        final SmellDatasetGenerationService service = new SmellDatasetGenerationService(configuration);
        service.generate();
    }
}
