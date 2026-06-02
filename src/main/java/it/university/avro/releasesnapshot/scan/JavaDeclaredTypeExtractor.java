package it.university.avro.releasesnapshot.scan;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaDeclaredTypeExtractor {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+(\\w[\\w.]*)\\s*;");

    private static final Pattern TYPE_PATTERN =
            Pattern.compile("(?m)\\b(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)\\b");

    private final JavaParser javaParser;
    private final LogicalClassPathResolver logicalClassPathResolver;

    public JavaDeclaredTypeExtractor(final LogicalClassPathResolver logicalClassPathResolver) {
        this.logicalClassPathResolver = Objects.requireNonNull(
                logicalClassPathResolver,
                "logicalClassPathResolver must not be null"
        );

        final ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

        this.javaParser = new JavaParser(parserConfiguration);
    }

    public List<ExtractedJavaType> extract(final JavaSourceUnit sourceUnit) {
        Objects.requireNonNull(sourceUnit, "sourceUnit must not be null");

        final Set<ExtractedJavaType> extractedTypes = new LinkedHashSet<>();
        final String sourceCode = sourceUnit.sourceCode();
        final String packageNameFromText = extractPackageName(sourceCode);

        addParserTypes(sourceUnit, sourceCode, packageNameFromText, extractedTypes);
        addLexicalTypes(sourceUnit, sourceCode, packageNameFromText, extractedTypes);

        if (extractedTypes.isEmpty()) {
            return fallbackToFileName(sourceUnit);
        }

        return List.copyOf(extractedTypes);
    }

    private void addParserTypes(
            final JavaSourceUnit sourceUnit,
            final String sourceCode,
            final String fallbackPackageName,
            final Set<ExtractedJavaType> extractedTypes
    ) {
        try {
            final ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);
            parseResult.getResult().ifPresent(compilationUnit -> addCompilationUnitTypes(
                    sourceUnit,
                    fallbackPackageName,
                    extractedTypes,
                    compilationUnit
            ));
        } catch (Exception ignored) {
            // Fallback lessicale sotto: alcuni sorgenti storici di Avro non sono parsabili con JavaParser moderno.
        }
    }

    private void addCompilationUnitTypes(
            final JavaSourceUnit sourceUnit,
            final String fallbackPackageName,
            final Set<ExtractedJavaType> extractedTypes,
            final CompilationUnit compilationUnit
    ) {
        final String packageName = compilationUnit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString())
                .orElse(fallbackPackageName);

        for (TypeDeclaration<?> typeDeclaration : compilationUnit.getTypes()) {
            final String typeName = typeDeclaration.getNameAsString();
            addExtractedType(sourceUnit.archivePath(), packageName, typeName, extractedTypes);
        }
    }

    private void addLexicalTypes(
            final JavaSourceUnit sourceUnit,
            final String sourceCode,
            final String packageName,
            final Set<ExtractedJavaType> extractedTypes
    ) {
        final String sanitizedSource = stripCommentsAndStrings(sourceCode);
        final Matcher matcher = TYPE_PATTERN.matcher(sanitizedSource);

        while (matcher.find()) {
            addExtractedType(sourceUnit.archivePath(), packageName, matcher.group(2), extractedTypes);
        }
    }

    private void addExtractedType(
            final String archivePath,
            final String packageName,
            final String typeName,
            final Set<ExtractedJavaType> extractedTypes
    ) {
        extractedTypes.add(new ExtractedJavaType(
                logicalClassPathResolver.resolve(archivePath, packageName, typeName),
                typeName
        ));
    }

    private String extractPackageName(final String sourceCode) {
        final Matcher matcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String stripCommentsAndStrings(final String sourceCode) {
        final SourceSanitizer sanitizer = new SourceSanitizer(sourceCode);
        return sanitizer.sanitize();
    }

    private List<ExtractedJavaType> fallbackToFileName(final JavaSourceUnit sourceUnit) {
        final String archivePath = sourceUnit.archivePath().replace('\\', '/');
        final int lastSlash = archivePath.lastIndexOf('/');
        final String fileName = lastSlash >= 0
                ? archivePath.substring(lastSlash + 1)
                : archivePath;

        if (!fileName.endsWith(".java")) {
            return List.of();
        }

        final String typeName = fileName.substring(0, fileName.length() - ".java".length());

        final List<ExtractedJavaType> fallback = new ArrayList<>();
        fallback.add(new ExtractedJavaType(
                logicalClassPathResolver.resolve(archivePath, "", typeName),
                typeName
        ));
        return fallback;
    }

    private static final class SourceSanitizer {

        private final String sourceCode;
        private final StringBuilder result;
        private boolean inLineComment;
        private boolean inBlockComment;
        private boolean inString;
        private boolean inChar;
        private boolean escaped;

        private SourceSanitizer(final String sourceCode) {
            this.sourceCode = sourceCode;
            this.result = new StringBuilder(sourceCode.length());
        }

        private String sanitize() {
            int index = 0;
            while (index < sourceCode.length()) {
                index = consume(index);
            }
            return result.toString();
        }

        private int consume(final int index) {
            final char current = sourceCode.charAt(index);
            final char next = nextCharacter(index);

            if (inLineComment) {
                return consumeLineComment(index, current);
            }
            if (inBlockComment) {
                return consumeBlockComment(index, current, next);
            }
            if (inString) {
                return consumeQuoted(index, current, '"');
            }
            if (inChar) {
                return consumeQuoted(index, current, '\'');
            }
            return consumeDefault(index, current, next);
        }

        private char nextCharacter(final int index) {
            return index + 1 < sourceCode.length() ? sourceCode.charAt(index + 1) : '\0';
        }

        private int consumeLineComment(final int index, final char current) {
            if (current == '\n') {
                inLineComment = false;
                result.append('\n');
            } else {
                result.append(' ');
            }
            return index + 1;
        }

        private int consumeBlockComment(final int index, final char current, final char next) {
            if (current == '*' && next == '/') {
                inBlockComment = false;
                result.append("  ");
                return index + 2;
            }
            appendSanitized(current);
            return index + 1;
        }

        private int consumeQuoted(final int index, final char current, final char closingQuote) {
            if (!escaped && current == closingQuote) {
                closeQuoted(closingQuote);
            }
            escaped = !escaped && current == '\\';
            appendSanitized(current);
            return index + 1;
        }

        private int consumeDefault(final int index, final char current, final char next) {
            if (current == '/' && next == '/') {
                inLineComment = true;
                result.append("  ");
                return index + 2;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                result.append("  ");
                return index + 2;
            }
            if (current == '"') {
                inString = true;
                escaped = false;
                result.append(' ');
                return index + 1;
            }
            if (current == '\'') {
                inChar = true;
                escaped = false;
                result.append(' ');
                return index + 1;
            }
            result.append(current);
            return index + 1;
        }

        private void appendSanitized(final char current) {
            result.append(current == '\n' ? '\n' : ' ');
        }

        private void closeQuoted(final char closingQuote) {
            if (closingQuote == '"') {
                inString = false;
            } else {
                inChar = false;
            }
        }
    }
}
