package it.university.avro.releasesnapshot.github;

import java.nio.file.Path;
import java.util.Objects;

public final class GitHubArchiveDownloader {

    private final GitHubApiClient gitHubApiClient;

    public GitHubArchiveDownloader(final GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = Objects.requireNonNull(gitHubApiClient, "gitHubApiClient must not be null");
    }

    public Path downloadReleaseArchive(final String tagName) {
        return gitHubApiClient.downloadZipball(tagName);
    }
}