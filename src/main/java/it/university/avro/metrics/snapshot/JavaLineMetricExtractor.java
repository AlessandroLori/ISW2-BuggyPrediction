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
        final boolean[] codeLines = new boolean[normalizedLength(lines.length)];
        final boolean[] commentLines = new boolean[normalizedLength(lines.length)];

        markSourceLines(normalized, codeLines, commentLines);

        final LineCounts lineCounts = countMarkedLines(codeLines, commentLines);
        final StructuralMetrics structuralMetrics = structureMetricExtractor.extract(sourceCode);
        return new StaticMetrics(
                lineCounts.codeLines(),
                lineCounts.commentLines(),
                structuralMetrics.nestingDepth(),
                structuralMetrics.decisionPoints()
        );
    }

    private int normalizedLength(final int lineCount) {
        return lineCount == 0 ? 1 : lineCount;
    }

    private void markSourceLines(
            final String sourceCode,
            final boolean[] codeLines,
            final boolean[] commentLines
    ) {
        final LineScanState state = new LineScanState();
        int index = 0;

        while (index < sourceCode.length()) {
            index = consumeCharacter(sourceCode, index, state, codeLines, commentLines);
        }
    }

    private int consumeCharacter(
            final String sourceCode,
            final int index,
            final LineScanState state,
            final boolean[] codeLines,
            final boolean[] commentLines
    ) {
        final char current = sourceCode.charAt(index);
        final char next = nextCharacter(sourceCode, index);

        if (current == '\n') {
            state.advanceLine();
            return index + 1;
        }
        if (state.inString()) {
            return consumeQuotedContent(index, current, state, codeLines, '"');
        }
        if (state.inChar()) {
            return consumeQuotedContent(index, current, state, codeLines, '\'');
        }
        if (state.inBlockComment()) {
            return consumeBlockComment(index, current, next, state, commentLines);
        }
        return consumeDefaultContent(sourceCode, index, current, next, state, codeLines, commentLines);
    }

    private char nextCharacter(final String sourceCode, final int index) {
        return index + 1 < sourceCode.length() ? sourceCode.charAt(index + 1) : '\0';
    }

    private int consumeQuotedContent(
            final int index,
            final char current,
            final LineScanState state,
            final boolean[] codeLines,
            final char closingQuote
    ) {
        if (!state.escaped() && current == closingQuote) {
            state.closeQuotedContent(closingQuote);
        }
        state.updateEscape(current);
        codeLines[state.lineIndex()] = true;
        return index + 1;
    }

    private int consumeBlockComment(
            final int index,
            final char current,
            final char next,
            final LineScanState state,
            final boolean[] commentLines
    ) {
        commentLines[state.lineIndex()] = true;
        if (current == '*' && next == '/') {
            state.closeBlockComment();
            return index + 2;
        }
        return index + 1;
    }

    private int consumeDefaultContent(
            final String sourceCode,
            final int index,
            final char current,
            final char next,
            final LineScanState state,
            final boolean[] codeLines,
            final boolean[] commentLines
    ) {
        if (current == '/' && next == '/') {
            commentLines[state.lineIndex()] = true;
            return skipLineComment(sourceCode, index + 2);
        }
        if (current == '/' && next == '*') {
            commentLines[state.lineIndex()] = true;
            state.openBlockComment();
            return index + 2;
        }
        if (current == '"') {
            state.openString();
            codeLines[state.lineIndex()] = true;
            return index + 1;
        }
        if (current == '\'') {
            state.openChar();
            codeLines[state.lineIndex()] = true;
            return index + 1;
        }
        if (!Character.isWhitespace(current)) {
            codeLines[state.lineIndex()] = true;
        }
        return index + 1;
    }

    private int skipLineComment(final String sourceCode, final int startIndex) {
        int index = startIndex;
        while (index < sourceCode.length() && sourceCode.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private LineCounts countMarkedLines(final boolean[] codeLines, final boolean[] commentLines) {
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

        return new LineCounts(loc, comments);
    }

    private record LineCounts(int codeLines, int commentLines) {
    }

    private static final class LineScanState {

        private int lineIndex;
        private boolean inBlockComment;
        private boolean inString;
        private boolean inChar;
        private boolean escaped;

        int lineIndex() {
            return lineIndex;
        }

        boolean inBlockComment() {
            return inBlockComment;
        }

        boolean inString() {
            return inString;
        }

        boolean inChar() {
            return inChar;
        }

        boolean escaped() {
            return escaped;
        }

        void advanceLine() {
            lineIndex++;
            escaped = false;
        }

        void openBlockComment() {
            inBlockComment = true;
        }

        void closeBlockComment() {
            inBlockComment = false;
        }

        void openString() {
            inString = true;
            escaped = false;
        }

        void openChar() {
            inChar = true;
            escaped = false;
        }

        void closeQuotedContent(final char closingQuote) {
            if (closingQuote == '"') {
                inString = false;
            } else {
                inChar = false;
            }
        }

        void updateEscape(final char current) {
            escaped = !escaped && current == '\\';
        }
    }
}
