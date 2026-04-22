package it.university.avro.metrics.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TemporaryGitRepository implements AutoCloseable {

    private final Path repositoryRoot;
    private final GitCommandExecutor gitCommandExecutor;
    private final ReleaseTagResolver releaseTagResolver;

    private TemporaryGitRepository(
            final Path repositoryRoot,
            final GitCommandExecutor gitCommandExecutor,
            final ReleaseTagResolver releaseTagResolver
    ) {
        this.repositoryRoot = repositoryRoot;
        this.gitCommandExecutor = gitCommandExecutor;
        this.releaseTagResolver = releaseTagResolver;
    }

    public static TemporaryGitRepository cloneRepository(final String repositoryUrl) {
        final GitCommandExecutor executor = new GitCommandExecutor();
        final ReleaseTagResolver tagResolver = new ReleaseTagResolver(executor);

        try {
            final Path tempDirectory = Files.createTempDirectory("avro-metrics-git-");
            final Path repositoryRoot = tempDirectory.resolve("repo");

            executor.executeOrThrow(
                    tempDirectory,
                    List.of("git", "clone", "--quiet", "--no-checkout", repositoryUrl, repositoryRoot.toString())
            );

            return new TemporaryGitRepository(repositoryRoot, executor, tagResolver);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create temporary directory for git repository", exception);
        }
    }

    public Path repositoryRoot() {
        return repositoryRoot;
    }

    public Optional<String> resolveTag(final String version) {
        return releaseTagResolver.resolveTag(repositoryRoot, version);
    }

    public Optional<String> readFileAtTag(final String tag, final String classPath) {
        final GitCommandResult result = gitCommandExecutor.execute(
                repositoryRoot,
                List.of("git", "show", tag + ":" + classPath)
        );

        if (!result.isSuccess()) {
            return Optional.empty();
        }

        return Optional.of(result.output());
    }

    public List<String> listPathsAtTagByFileName(final String tag, final String fileName) {
        final GitCommandResult result = gitCommandExecutor.execute(
                repositoryRoot,
                List.of("git", "ls-tree", "-r", "--name-only", tag)
        );

        if (!result.isSuccess() || result.output().isBlank()) {
            return List.of();
        }

        return result.output()
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.equals(fileName) || line.endsWith("/" + fileName))
                .collect(Collectors.toList());
    }

    public String gitLogForPathInReleaseWindow(
            final String previousTagExclusive,
            final String currentTagInclusive,
            final String classPath
    ) {
        final String revisionRange = previousTagExclusive == null || previousTagExclusive.isBlank()
                ? currentTagInclusive
                : previousTagExclusive + ".." + currentTagInclusive;

        final GitCommandResult result = gitCommandExecutor.execute(
                repositoryRoot,
                List.of(
                        "git",
                        "log",
                        "--follow",
                        "--date=iso-strict",
                        "--format=@@COMMIT@@%H\u001f%an\u001f%cI\u001f%s",
                        "--numstat",
                        revisionRange,
                        "--",
                        classPath
                )
        );

        if (!result.isSuccess()) {
            return "";
        }

        return result.output();
    }

    public String gitLogForPathUntilTag(
            final String currentTagInclusive,
            final String classPath
    ) {
        final GitCommandResult result = gitCommandExecutor.execute(
                repositoryRoot,
                List.of(
                        "git",
                        "log",
                        "--follow",
                        "--date=iso-strict",
                        "--format=@@COMMIT@@%H\u001f%an\u001f%cI\u001f%s",
                        "--numstat",
                        currentTagInclusive,
                        "--",
                        classPath
                )
        );

        if (!result.isSuccess()) {
            return "";
        }

        return result.output();
    }

    public Optional<LocalDate> resolveCommitDateForRef(final String ref) {
        final GitCommandResult result = gitCommandExecutor.execute(
                repositoryRoot,
                List.of("git", "log", "-1", "--date=iso-strict", "--format=%cI", ref)
        );

        if (!result.isSuccess() || result.output().isBlank()) {
            return Optional.empty();
        }

        final String rawValue = result.output().trim();
        final String normalized = rawValue.length() >= 10 ? rawValue.substring(0, 10) : rawValue;
        return Optional.of(LocalDate.parse(normalized));
    }

    public int countChangedFilesInCommit(final String commitHash) {
        final GitCommandResult result = gitCommandExecutor.execute(
                repositoryRoot,
                List.of(
                        "git",
                        "show",
                        "--format=",
                        "--name-only",
                        "--diff-filter=ACMRTUXB",
                        commitHash
                )
        );

        if (!result.isSuccess() || result.output().isBlank()) {
            return 0;
        }

        return (int) result.output()
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .count();
    }

    @Override
    public void close() {
        try {
            Files.walk(repositoryRoot.getParent())
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public String gitLogAllWithChangedPaths() {
        final GitCommandResult result = gitCommandExecutor.execute(
                repositoryRoot,
                List.of(
                        "git",
                        "log",
                        "--all",
                        "--date=iso-strict",
                        "--format=@@COMMIT@@%H\u001f%cI\u001f%s",
                        "--name-only",
                        "--diff-filter=AMR"
                )
        );

        if (!result.isSuccess()) {
            return "";
        }

        return result.output();
    }

}
