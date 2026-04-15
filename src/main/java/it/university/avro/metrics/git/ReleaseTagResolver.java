package it.university.avro.metrics.git;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class ReleaseTagResolver {

    private final GitCommandExecutor gitCommandExecutor;

    public ReleaseTagResolver(final GitCommandExecutor gitCommandExecutor) {
        this.gitCommandExecutor = gitCommandExecutor;
    }

    public Optional<String> resolveTag(final Path repositoryRoot, final String version) {
        final List<String> candidates = List.of(
                "release-" + version,
                "avro-" + version,
                version
        );

        for (String candidate : candidates) {
            final GitCommandResult result = gitCommandExecutor.execute(
                    repositoryRoot,
                    List.of("git", "rev-parse", "-q", "--verify", "refs/tags/" + candidate)
            );

            if (result.isSuccess()) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }
}