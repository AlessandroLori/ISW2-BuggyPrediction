package it.university.avro.releasesnapshot.scan;

public final class JavaClassNameExtractor {

    public String extractClassName(final String classPath) {
        final int lastSlash = classPath.lastIndexOf('/');
        final String fileName = lastSlash >= 0 ? classPath.substring(lastSlash + 1) : classPath;

        if (!fileName.endsWith(".java")) {
            throw new IllegalArgumentException("Not a Java source file: " + classPath);
        }

        return fileName.substring(0, fileName.length() - ".java".length());
    }
}