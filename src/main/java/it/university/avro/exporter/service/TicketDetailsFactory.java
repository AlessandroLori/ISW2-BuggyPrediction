package it.university.avro.exporter.service;

import it.university.avro.exporter.domain.JiraIssue;
import it.university.avro.exporter.domain.JiraVersion;
import it.university.avro.exporter.domain.TicketDetailsRecord;
import it.university.avro.exporter.domain.VersionCatalog;
import it.university.avro.exporter.domain.VersionReference;
import it.university.avro.exporter.util.VersionNameComparator;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class TicketDetailsFactory {

    private static final String NOT_AVAILABLE = "n/a";

    private final VersionNameComparator versionNameComparator = new VersionNameComparator();

    public Optional<TicketDetailsRecord> create(final JiraIssue issue, final VersionCatalog versionCatalog) {
        Objects.requireNonNull(issue, "issue must not be null");
        Objects.requireNonNull(versionCatalog, "versionCatalog must not be null");

        final ResolvedVersionGroup affectedVersions = resolveVersions(issue.affectedVersionReferences(), versionCatalog);
        if (affectedVersions.status() == ResolutionStatus.INVALID) {
            return Optional.empty();
        }

        final JiraVersion fixedVersion = resolveFixedVersion(issue, versionCatalog);
        if (fixedVersion == null) {
            return Optional.empty();
        }

        if (fixedVersion.releaseDate().isBefore(issue.createdDate())) {
            return Optional.empty();
        }

        final JiraVersion openingVersion = resolveOpeningVersion(issue.createdDate(), versionCatalog);
        if (openingVersion == null) {
            return Optional.empty();
        }

        // Nuova regola:
        // se OV < una qualsiasi AV, il ticket va scartato
        if (hasAffectedVersionNewerThanOpeningVersion(affectedVersions.versions(), openingVersion)) {
            return Optional.empty();
        }

        final List<JiraVersion> normalizedAffectedVersions =
                removeFixedVersionFromAffectedVersions(affectedVersions.versions(), fixedVersion);

        if (hasAffectedVersionNewerThanFixedVersion(normalizedAffectedVersions, fixedVersion)) {
            return Optional.empty();
        }

        return Optional.of(new TicketDetailsRecord(
                issue.ticketId(),
                formatDate(issue.createdDate()),
                formatDate(issue.closedDate()),
                openingVersion.name(),
                formatDate(openingVersion.releaseDate()),
                formatAffectedVersions(normalizedAffectedVersions),
                normalizedAffectedVersions.size(),
                fixedVersion.name(),
                formatDate(fixedVersion.releaseDate())
        ));
    }

    private ResolvedVersionGroup resolveVersions(
            final List<VersionReference> references,
            final VersionCatalog versionCatalog
    ) {
        if (references == null || references.isEmpty()) {
            return ResolvedVersionGroup.notSpecified();
        }

        final Set<JiraVersion> resolvedVersions = new LinkedHashSet<>();
        for (VersionReference reference : references) {
            final JiraVersion version = versionCatalog.findByReference(reference);
            if (version == null) {
                return ResolvedVersionGroup.invalid();
            }
            if (version.released() && version.releaseDate() == null) {
                return ResolvedVersionGroup.invalid();
            }
            if (version.released() && version.releaseDate() != null) {
                resolvedVersions.add(version);
            }
        }

        if (resolvedVersions.isEmpty()) {
            return ResolvedVersionGroup.invalid();
        }

        return ResolvedVersionGroup.resolved(
                resolvedVersions.stream()
                        .sorted(this::compareVersions)
                        .toList()
        );
    }

    private JiraVersion resolveFixedVersion(final JiraIssue issue, final VersionCatalog versionCatalog) {
        final ResolvedVersionGroup explicitFixedVersions = resolveVersions(issue.fixedVersionReferences(), versionCatalog);
        if (explicitFixedVersions.status() == ResolutionStatus.INVALID) {
            return null;
        }
        if (explicitFixedVersions.status() == ResolutionStatus.RESOLVED) {
            return explicitFixedVersions.versions().get(0);
        }

        return inferFixedVersionFromClosedDate(issue.closedDate(), versionCatalog);
    }

    private JiraVersion inferFixedVersionFromClosedDate(
            final LocalDate closedDate,
            final VersionCatalog versionCatalog
    ) {
        for (JiraVersion version : versionCatalog.releasedVersionsWithDate()) {
            if (!version.releaseDate().isBefore(closedDate)) {
                return version;
            }
        }
        return null;
    }

    private JiraVersion resolveOpeningVersion(final LocalDate createdDate, final VersionCatalog versionCatalog) {
        JiraVersion candidate = null;
        for (JiraVersion version : versionCatalog.releasedVersionsWithDate()) {
            if (!version.releaseDate().isAfter(createdDate)) {
                candidate = version;
            } else {
                break;
            }
        }
        return candidate;
    }

    private List<JiraVersion> removeFixedVersionFromAffectedVersions(
            final List<JiraVersion> affectedVersions,
            final JiraVersion fixedVersion
    ) {
        return affectedVersions.stream()
                .filter(version -> !isSameVersion(version, fixedVersion))
                .sorted(this::compareVersions)
                .toList();
    }

    private boolean hasAffectedVersionNewerThanFixedVersion(
            final List<JiraVersion> affectedVersions,
            final JiraVersion fixedVersion
    ) {
        return affectedVersions.stream()
                .map(JiraVersion::name)
                .anyMatch(affectedVersionName ->
                        versionNameComparator.compare(affectedVersionName, fixedVersion.name()) > 0
                );
    }

    private boolean hasAffectedVersionNewerThanOpeningVersion(
            final List<JiraVersion> affectedVersions,
            final JiraVersion openingVersion
    ) {
        return affectedVersions.stream()
                .map(JiraVersion::name)
                .anyMatch(affectedVersionName ->
                        versionNameComparator.compare(affectedVersionName, openingVersion.name()) > 0
                );
    }

    private boolean isSameVersion(final JiraVersion left, final JiraVersion right) {
        if (left.id() != null && right.id() != null) {
            return left.id().equals(right.id());
        }
        return Objects.equals(left.name(), right.name());
    }

    private int compareVersions(final JiraVersion left, final JiraVersion right) {
        final int byName = versionNameComparator.compare(left.name(), right.name());
        if (byName != 0) {
            return byName;
        }

        if (left.releaseDate() == null && right.releaseDate() == null) {
            return 0;
        }
        if (left.releaseDate() == null) {
            return -1;
        }
        if (right.releaseDate() == null) {
            return 1;
        }

        return left.releaseDate().compareTo(right.releaseDate());
    }

    private String formatAffectedVersions(final List<JiraVersion> affectedVersions) {
        if (affectedVersions.isEmpty()) {
            return NOT_AVAILABLE;
        }
        return affectedVersions.stream()
                .map(JiraVersion::name)
                .collect(Collectors.joining(";"));
    }

    private String formatDate(final LocalDate date) {
        return date == null ? NOT_AVAILABLE : date.toString();
    }

    private enum ResolutionStatus {
        NOT_SPECIFIED,
        INVALID,
        RESOLVED
    }

    private record ResolvedVersionGroup(ResolutionStatus status, List<JiraVersion> versions) {

        private static ResolvedVersionGroup notSpecified() {
            return new ResolvedVersionGroup(ResolutionStatus.NOT_SPECIFIED, List.of());
        }

        private static ResolvedVersionGroup invalid() {
            return new ResolvedVersionGroup(ResolutionStatus.INVALID, List.of());
        }

        private static ResolvedVersionGroup resolved(final List<JiraVersion> versions) {
            return new ResolvedVersionGroup(
                    ResolutionStatus.RESOLVED,
                    List.copyOf(Objects.requireNonNull(versions, "versions must not be null"))
            );
        }
    }
}