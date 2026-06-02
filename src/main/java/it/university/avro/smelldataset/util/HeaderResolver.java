package it.university.avro.smelldataset.util;

import java.util.List;
import java.util.Locale;

public final class HeaderResolver {

    private final String versionColumn;
    private final String classPathColumn;
    private final String smellColumn;
    private final String buggyColumn;

    private HeaderResolver(
            String versionColumn,
            String classPathColumn,
            String smellColumn,
            String buggyColumn
    ) {
        this.versionColumn = versionColumn;
        this.classPathColumn = classPathColumn;
        this.smellColumn = smellColumn;
        this.buggyColumn = buggyColumn;
    }

    public static HeaderResolver from(List<String> headers) {
        return new HeaderResolver(
                findRequired(headers, "version"),
                findRequired(headers, "classpath", "class_path", "class"),
                findRequired(headers, "nsmells", "smells", "number_of_smells"),
                findRequired(headers, "buggy")
        );
    }

    public String versionColumn() {
        return versionColumn;
    }

    public String classPathColumn() {
        return classPathColumn;
    }

    public String smellColumn() {
        return smellColumn;
    }

    public String buggyColumn() {
        return buggyColumn;
    }

    private static String findRequired(List<String> headers, String... acceptedNames) {
        for (String header : headers) {
            String normalizedHeader = normalize(header);

            for (String acceptedName : acceptedNames) {
                if (normalizedHeader.equals(normalize(acceptedName))) {
                    return header;
                }
            }
        }

        throw new IllegalStateException("Required column not found. Expected one of: " + String.join(", ", acceptedNames));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }
}
