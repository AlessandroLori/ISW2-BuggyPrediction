package it.university.avro.metrics.snapshot;

import it.university.avro.metrics.domain.StaticMetrics;

public final class JavaLineMetricExtractor {

    private final JavaStructureMetricExtractor structureMetricExtractor;

    public JavaLineMetricExtractor() {
        this.structureMetricExtractor = new JavaStructureMetricExtractor();
    }

    public StaticMetrics extract(final String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return StaticMetrics.empty();
        }

        final String normalized = sourceCode.replace("\r\n", "\n").replace('\r', '\n');
        final String[] lines = normalized.split("\n", -1);

        final boolean[] codeLines = new boolean[lines.length == 0 ? 1 : lines.length];
        final boolean[] commentLines = new boolean[lines.length == 0 ? 1 : lines.length];

        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        int lineIndex = 0;

        for (int index = 0; index < normalized.length(); index++) {
            final char current = normalized.charAt(index);
            final char next = index + 1 < normalized.length() ? normalized.charAt(index + 1) : '\0';

            if (current == '\n') {
                lineIndex++;
                escaped = false;
                continue;
            }

            if (inString) {
                if (!escaped && current == '"') {
                    inString = false;
                }
                escaped = !escaped && current == '\\';
                codeLines[lineIndex] = true;
                continue;
            }

            if (inChar) {
                if (!escaped && current == '\'') {
                    inChar = false;
                }
                escaped = !escaped && current == '\\';
                codeLines[lineIndex] = true;
                continue;
            }

            if (inBlockComment) {
                commentLines[lineIndex] = true;

                if (current == '*' && next == '/') {
                    commentLines[lineIndex] = true;
                    inBlockComment = false;
                    index++;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                commentLines[lineIndex] = true;
                while (index < normalized.length() && normalized.charAt(index) != '\n') {
                    index++;
                }
                index--;
                continue;
            }

            if (current == '/' && next == '*') {
                commentLines[lineIndex] = true;
                inBlockComment = true;
                index++;
                continue;
            }

            if (current == '"') {
                inString = true;
                codeLines[lineIndex] = true;
                escaped = false;
                continue;
            }

            if (current == '\'') {
                inChar = true;
                codeLines[lineIndex] = true;
                escaped = false;
                continue;
            }

            if (!Character.isWhitespace(current)) {
                codeLines[lineIndex] = true;
            }
        }

        int loc = 0;
        int comments = 0;

        for (int index = 0; index < codeLines.length; index++) {
            if (codeLines[index]) {
                loc++;
            }
            if (commentLines[index]) {
                comments++;
            }
        }

        final StructuralMetrics structuralMetrics = structureMetricExtractor.extract(sourceCode);
        return new StaticMetrics(
                loc,
                comments,
                structuralMetrics.nestingDepth(),
                structuralMetrics.decisionPoints()
        );
    }
}
