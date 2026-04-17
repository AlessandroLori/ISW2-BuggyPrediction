package it.university.avro.metrics.snapshot;

import it.university.avro.metrics.git.TemporaryGitRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaSourceLocator {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");

    public SourceLookupResult locate(
            final TemporaryGitRepository repository,
            final String tag,
            final String requestedPath
    ) {
        final Optional<String> exactSource = repository.readFileAtTag(tag, requestedPath);
        if (exactSource.isPresent() && !exactSource.get().isBlank()) {
            return SourceLookupResult.exact(requestedPath, exactSource.get());
        }

        final String fileName = extractFileName(requestedPath);
        final String expectedPackage = extractExpectedPackageFromPath(requestedPath);

        final List<String> candidates = repository.listPathsAtTagByFileName(tag, fileName);
        final List<String> matchingCandidates = new ArrayList<>();

        for (String candidatePath : candidates) {
            final Optional<String> candidateSource = repository.readFileAtTag(tag, candidatePath);
            if (candidateSource.isEmpty() || candidateSource.get().isBlank()) {
                continue;
            }

            final String detectedPackage = extractPackageFromSource(candidateSource.get()).orElse("");
            if (detectedPackage.equals(expectedPackage)) {
                matchingCandidates.add(candidatePath);
            }
        }

        if (matchingCandidates.size() == 1) {
            final String resolvedPath = matchingCandidates.get(0);
            final String sourceCode = repository.readFileAtTag(tag, resolvedPath).orElse("");
            if (!sourceCode.isBlank()) {
                return SourceLookupResult.recovered(requestedPath, resolvedPath, sourceCode);
            }
        }

        return SourceLookupResult.notFound(requestedPath);
    }

    private String extractFileName(final String path) {
        final int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String extractExpectedPackageFromPath(final String path) {
        final String normalized = path.replace('\\', '/');
        final String[] parts = normalized.split("/");

        int lastJavaIndex = -1;
        for (int index = 0; index < parts.length; index++) {
            if ("java".equals(parts[index])) {
                lastJavaIndex = index;
            }
        }

        if (lastJavaIndex < 0 || lastJavaIndex >= parts.length - 2) {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        for (int index = lastJavaIndex + 1; index < parts.length - 1; index++) {
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(parts[index]);
        }

        return builder.toString();
    }

    private Optional<String> extractPackageFromSource(final String sourceCode) {
        final Matcher matcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }
}