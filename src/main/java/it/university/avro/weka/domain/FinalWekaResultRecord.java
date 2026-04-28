package it.university.avro.weka.domain;

import java.util.Locale;

public record FinalWekaResultRecord(
        String dataset,
        String classifier,
        String fs,
        String balancing,
        double precision,
        double recall,
        double auc,
        double kappa,
        String npofb20
) {
    public String precisionAsCsv() {
        return formatDouble(precision);
    }

    public String recallAsCsv() {
        return formatDouble(recall);
    }

    public String aucAsCsv() {
        return formatDouble(auc);
    }

    public String kappaAsCsv() {
        return formatDouble(kappa);
    }

    private String formatDouble(final double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
