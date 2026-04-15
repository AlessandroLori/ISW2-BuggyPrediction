package it.university.avro.releasesnapshot.github;

import it.university.avro.releasesnapshot.domain.ReleaseCommitSnapshot;
import it.university.avro.releasesnapshot.domain.ReleaseInfo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GitHubTagResolver {

    private final GitHubApiClient gitHubApiClient;

    public GitHubTagResolver(final GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = Objects.requireNonNull(gitHubApiClient, "gitHubApiClient must not be null");
    }

    public Optional<ReleaseCommitSnapshot> resolve(final ReleaseInfo releaseInfo) {
        for (String candidateTag : candidateTags(releaseInfo.version())) {
            final Optional<String> commitSha = gitHubApiClient.getOptionalCommitShaForRef(candidateTag);

            if (commitSha.isEmpty()) {
                continue;
            }

            return Optional.of(new ReleaseCommitSnapshot(
                    releaseInfo.version(),
                    releaseInfo.releaseDate(),
                    candidateTag,
                    commitSha.get()
            ));
        }

        return Optional.empty();
    }

    private List<String> candidateTags(final String version) {
        return List.of(
                "release-" + version,
                "avro-" + version,
                version
        );
    }
}