package it.university.avro.metrics.util;

public final class ClassPathNormalizer {

    private ClassPathNormalizer() {
    }

    public static String normalize(final String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }

        final String normalized = rawPath.replace('\\', '/').trim();

        if (normalized.startsWith("org/")) {
            return normalized;
        }

        final int orgIndex = normalized.indexOf("org/");
        if (orgIndex >= 0) {
            return normalized.substring(orgIndex);
        }

        final int lastSlashIndex = normalized.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex + 1 < normalized.length()) {
            return normalized.substring(lastSlashIndex + 1);
        }

        return normalized;
    }
}