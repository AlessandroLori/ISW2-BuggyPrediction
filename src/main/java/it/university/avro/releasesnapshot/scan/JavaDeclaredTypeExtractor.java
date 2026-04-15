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
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_\\.]*)\\s*;");

    private static final Pattern TYPE_PATTERN =
            Pattern.compile(
                    "(?m)^\\s*(?:public|protected|private|abstract|static|final|sealed|non-sealed|strictfp\\s+)*" +
                            "(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"
            );

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

        try {
            final ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);

            if (parseResult.getResult().isPresent()) {
                final CompilationUnit compilationUnit = parseResult.getResult().get();

                final String packageName = compilationUnit.getPackageDeclaration()
                        .map(packageDeclaration -> packageDeclaration.getNameAsString())
                        .orElse(packageNameFromText);

                for (TypeDeclaration<?> typeDeclaration : compilationUnit.getTypes()) {
                    final String typeName = typeDeclaration.getNameAsString();

                    extractedTypes.add(new ExtractedJavaType(
                            logicalClassPathResolver.resolve(
                                    sourceUnit.archivePath(),
                                    packageName,
                                    typeName
                            ),
                            typeName
                    ));
                }
            }
        } catch (Exception ignored) {
            // fallback lessicale sotto
        }

        // Fallback generale: se il parser fallisce o non cattura tutto,
        // provo a recuperare i type name direttamente dal testo.
        final String sanitizedSource = stripCommentsAndStrings(sourceCode);
        final Matcher matcher = TYPE_PATTERN.matcher(sanitizedSource);

        while (matcher.find()) {
            final String typeName = matcher.group(2);

            extractedTypes.add(new ExtractedJavaType(
                    logicalClassPathResolver.resolve(
                            sourceUnit.archivePath(),
                            packageNameFromText,
                            typeName
                    ),
                    typeName
            ));
        }

        if (extractedTypes.isEmpty()) {
            return fallbackToFileName(sourceUnit);
        }

        return List.copyOf(extractedTypes);
    }

    private String extractPackageName(final String sourceCode) {
        final Matcher matcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String stripCommentsAndStrings(final String sourceCode) {
        final StringBuilder result = new StringBuilder(sourceCode.length());

        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        for (int index = 0; index < sourceCode.length(); index++) {
            final char current = sourceCode.charAt(index);
            final char next = index + 1 < sourceCode.length() ? sourceCode.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    result.append("  ");
                    index++;
                } else if (current == '\n') {
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (inString) {
                if (!escaped && current == '"') {
                    inString = false;
                }
                escaped = !escaped && current == '\\';
                result.append(current == '\n' ? '\n' : ' ');
                continue;
            }

            if (inChar) {
                if (!escaped && current == '\'') {
                    inChar = false;
                }
                escaped = !escaped && current == '\\';
                result.append(current == '\n' ? '\n' : ' ');
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                result.append("  ");
                index++;
                continue;
            }

            if (current == '/' && next == '*') {
                inBlockComment = true;
                result.append("  ");
                index++;
                continue;
            }

            if (current == '"') {
                inString = true;
                escaped = false;
                result.append(' ');
                continue;
            }

            if (current == '\'') {
                inChar = true;
                escaped = false;
                result.append(' ');
                continue;
            }

            result.append(current);
        }

        return result.toString();
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
}